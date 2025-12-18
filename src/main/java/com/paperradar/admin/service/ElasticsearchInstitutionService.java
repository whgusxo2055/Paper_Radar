package com.paperradar.admin.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import com.paperradar.admin.model.InstitutionSummary;
import com.paperradar.ingest.openalex.OpenAlexInstitutionClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ElasticsearchInstitutionService implements InstitutionService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchInstitutionService.class);
    private static final String INDEX = "institutions";

    private final ElasticsearchClient client;
    private final OpenAlexInstitutionClient openAlexInstitutionClient;

    @Override
    public InstitutionSummary upsertInstitution(String id, String displayName, String alias) {
        String normalizedId = normalizeOpenAlexInstitutionId(id);
        if (normalizedId.isBlank()) {
            return new InstitutionSummary("", "", false);
        }

        try {
            GetResponse<Map> existing = client.get(g -> g.index(INDEX).id(normalizedId), Map.class);
            String existingName = existing.found() && existing.source() != null ? asString(existing.source().get("display_name")) : null;
            Boolean existingActive = existing.found() && existing.source() != null ? asBoolean(existing.source().get("active")) : null;
            List<String> aliases = existing.found() && existing.source() != null ? asStringList(existing.source().get("name_aliases")) : new ArrayList<>();

            String finalName = (displayName == null || displayName.isBlank()) ? (existingName == null ? "" : existingName) : displayName.trim();
            if (finalName.isBlank()) {
                finalName = fetchInstitutionNameFromOpenAlex(normalizedId);
            }
            boolean finalActive = existingActive == null || existingActive;
            if (alias != null && !alias.isBlank()) {
                String trimmedAlias = alias.trim();
                if (!aliases.contains(trimmedAlias)) {
                    aliases.add(trimmedAlias);
                }
            }

            Map<String, Object> doc = Map.of(
                    "id", normalizedId,
                    "display_name", finalName,
                    "name_aliases", aliases,
                    "active", finalActive,
                    "updated_at", Instant.now().toString()
            );

            client.index(i -> i.index(INDEX).id(normalizedId).document(doc));
            return new InstitutionSummary(normalizedId, finalName, finalActive);
        } catch (Exception e) {
            log.error("Failed to upsert institution {}", normalizedId, e);
            String name = (displayName == null || displayName.isBlank()) ? fetchInstitutionNameFromOpenAlex(normalizedId) : displayName;
            return new InstitutionSummary(normalizedId, name, false);
        }
    }

    @Override
    public InstitutionSummary setActive(String id, boolean active) {
        String normalizedId = normalizeOpenAlexInstitutionId(id);
        if (normalizedId.isBlank()) {
            return new InstitutionSummary("", "", false);
        }
        try {
            GetResponse<Map> existing = client.get(g -> g.index(INDEX).id(normalizedId), Map.class);
            String name = existing.found() && existing.source() != null ? asString(existing.source().get("display_name")) : "";
            if (name.isBlank()) {
                name = fetchInstitutionNameFromOpenAlex(normalizedId);
            }
            List<String> aliases = existing.found() && existing.source() != null ? asStringList(existing.source().get("name_aliases")) : List.of();

            Map<String, Object> doc = Map.of(
                    "id", normalizedId,
                    "display_name", name,
                    "name_aliases", aliases,
                    "active", active,
                    "updated_at", Instant.now().toString()
            );
            client.index(i -> i.index(INDEX).id(normalizedId).document(doc));
            return new InstitutionSummary(normalizedId, name, active);
        } catch (Exception e) {
            log.error("Failed to set active={} for institution {}", active, normalizedId, e);
            return new InstitutionSummary(normalizedId, "", active);
        }
    }

    @Override
    public List<InstitutionSummary> getByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        try {
            List<String> normalizedIds = ids.stream()
                    .map(this::normalizeOpenAlexInstitutionId)
                    .filter(s -> s != null && !s.isBlank())
                    .distinct()
                    .toList();
            MgetResponse<Map> response = client.mget(m -> {
                m.index(INDEX);
                normalizedIds.forEach(id -> m.docs(d -> d.id(id)));
                return m;
            }, Map.class);

            List<InstitutionSummary> out = new ArrayList<>();
            response.docs().forEach(doc -> {
                if (!doc.isResult() || doc.result() == null) {
                    out.add(new InstitutionSummary("", "", false));
                    return;
                }
                String id = doc.result().id();
                Map<?, ?> src = doc.result().source();
                if (src == null) {
                    out.add(new InstitutionSummary(id, fallbackName(id), false));
                    return;
                }
                String displayName = asString(src.get("display_name"));
                if (displayName.isBlank()) {
                    displayName = fallbackName(id);
                }
                out.add(new InstitutionSummary(
                        id,
                        displayName,
                        Boolean.TRUE.equals(asBoolean(src.get("active")))
                ));
            });
            return out;
        } catch (Exception e) {
            log.error("Failed to mget institutions.", e);
            return ids.stream().map(id -> new InstitutionSummary(id, fallbackName(id), false)).toList();
        }
    }

    private String normalizeOpenAlexInstitutionId(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isBlank()) {
            return "";
        }
        if (trimmed.startsWith("https://openalex.org/")) {
            return trimmed.substring("https://openalex.org/".length());
        }
        if (trimmed.startsWith("http://openalex.org/")) {
            return trimmed.substring("http://openalex.org/".length());
        }
        return trimmed;
    }

    private String fetchInstitutionNameFromOpenAlex(String normalizedId) {
        try {
            var inst = openAlexInstitutionClient.getInstitution(normalizedId);
            if (inst != null && inst.displayName() != null && !inst.displayName().isBlank()) {
                return inst.displayName().trim();
            }
        } catch (Exception e) {
            log.debug("Failed to fetch institution name from OpenAlex: {}", normalizedId, e);
        }
        return fallbackName(normalizedId);
    }

    private String fallbackName(String id) {
        if (id == null) {
            return "";
        }
        String trimmed = id.trim();
        int idx = trimmed.lastIndexOf('/');
        if (idx >= 0 && idx + 1 < trimmed.length()) {
            String tail = trimmed.substring(idx + 1);
            if (!tail.isBlank()) {
                return tail;
            }
        }
        return trimmed;
    }

    private String asString(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private Boolean asBoolean(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        return Boolean.valueOf(String.valueOf(v));
    }

    private List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o == null) continue;
                out.add(String.valueOf(o));
            }
            return out;
        }
        return new ArrayList<>();
    }
}
