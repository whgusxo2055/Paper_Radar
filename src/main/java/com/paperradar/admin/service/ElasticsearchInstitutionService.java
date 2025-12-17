package com.paperradar.admin.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import com.paperradar.admin.model.InstitutionSummary;
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

    @Override
    public InstitutionSummary upsertInstitution(String id, String displayName, String alias) {
        String normalizedId = id == null ? "" : id.trim();
        if (normalizedId.isBlank()) {
            return new InstitutionSummary("", "", false);
        }

        try {
            GetResponse<Map> existing = client.get(g -> g.index(INDEX).id(normalizedId), Map.class);
            String existingName = existing.found() && existing.source() != null ? asString(existing.source().get("display_name")) : null;
            Boolean existingActive = existing.found() && existing.source() != null ? asBoolean(existing.source().get("active")) : null;
            List<String> aliases = existing.found() && existing.source() != null ? asStringList(existing.source().get("name_aliases")) : new ArrayList<>();

            String finalName = (displayName == null || displayName.isBlank()) ? (existingName == null ? "" : existingName) : displayName.trim();
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
            return new InstitutionSummary(normalizedId, displayName == null ? "" : displayName, false);
        }
    }

    @Override
    public InstitutionSummary setActive(String id, boolean active) {
        String normalizedId = id == null ? "" : id.trim();
        if (normalizedId.isBlank()) {
            return new InstitutionSummary("", "", false);
        }
        try {
            GetResponse<Map> existing = client.get(g -> g.index(INDEX).id(normalizedId), Map.class);
            String name = existing.found() && existing.source() != null ? asString(existing.source().get("display_name")) : "";
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
            MgetResponse<Map> response = client.mget(m -> {
                m.index(INDEX);
                ids.forEach(id -> m.docs(d -> d.id(id)));
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
                    out.add(new InstitutionSummary(id, id, false));
                    return;
                }
                out.add(new InstitutionSummary(
                        id,
                        asString(src.get("display_name")),
                        Boolean.TRUE.equals(asBoolean(src.get("active")))
                ));
            });
            return out;
        } catch (Exception e) {
            log.error("Failed to mget institutions.", e);
            return ids.stream().map(id -> new InstitutionSummary(id, id, false)).toList();
        }
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
