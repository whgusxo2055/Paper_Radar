package com.paperradar.admin.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.paperradar.infra.es.ElasticsearchErrorUtil;
import com.paperradar.admin.model.ActiveConfig;
import com.paperradar.util.KeywordNormalizeUtil;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ElasticsearchConfigService implements ConfigService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchConfigService.class);

    private static final String INDEX = "keyword_configs";
    private static final String ID = "active_config";

    private final ElasticsearchClient client;

    @Override
    public ActiveConfig getActiveConfig() {
        try {
            GetResponse<Map> response = client.get(g -> g.index(INDEX).id(ID), Map.class);
            if (!response.found() || response.source() == null) {
                ActiveConfig seeded = seedIfMissing();
                return seeded;
            }
            return fromSource(response.source());
        } catch (ElasticsearchException e) {
            if (ElasticsearchErrorUtil.isIndexNotFound(e)) {
                log.warn("Index {} not found. Returning empty active_config.", INDEX);
                return new ActiveConfig(List.of(), List.of(), List.of(), List.of(), Instant.EPOCH);
            }
            log.error("Failed to load active_config.", e);
            return new ActiveConfig(List.of(), List.of(), List.of(), List.of(), Instant.EPOCH);
        } catch (Exception e) {
            log.error("Failed to load active_config.", e);
            return new ActiveConfig(List.of(), List.of(), List.of(), List.of(), Instant.EPOCH);
        }
    }

    @Override
    public ActiveConfig enableKeyword(String keyword) {
        String normalized = KeywordNormalizeUtil.normalize(keyword);
        if (normalized.isBlank()) {
            return getActiveConfig();
        }
        return update(cfg -> {
            Set<String> enabled = new LinkedHashSet<>(cfg.enabledKeywords());
            Set<String> disabled = new LinkedHashSet<>(cfg.disabledKeywords());
            enabled.add(normalized);
            disabled.remove(normalized);
            return new ActiveConfig(List.copyOf(enabled), List.copyOf(disabled), cfg.enabledInstitutions(), cfg.disabledInstitutions(), Instant.now());
        });
    }

    @Override
    public ActiveConfig disableKeyword(String keyword) {
        String normalized = KeywordNormalizeUtil.normalize(keyword);
        if (normalized.isBlank()) {
            return getActiveConfig();
        }
        return update(cfg -> {
            Set<String> enabled = new LinkedHashSet<>(cfg.enabledKeywords());
            Set<String> disabled = new LinkedHashSet<>(cfg.disabledKeywords());
            enabled.remove(normalized);
            disabled.add(normalized);
            return new ActiveConfig(List.copyOf(enabled), List.copyOf(disabled), cfg.enabledInstitutions(), cfg.disabledInstitutions(), Instant.now());
        });
    }

    @Override
    public ActiveConfig enableInstitution(String instId) {
        String id = normalizeInstitutionId(instId);
        if (id.isBlank()) {
            return getActiveConfig();
        }
        return update(cfg -> {
            Set<String> enabled = new LinkedHashSet<>(cfg.enabledInstitutions());
            Set<String> disabled = new LinkedHashSet<>(cfg.disabledInstitutions());
            enabled.add(id);
            disabled.remove(id);
            return new ActiveConfig(cfg.enabledKeywords(), cfg.disabledKeywords(), List.copyOf(enabled), List.copyOf(disabled), Instant.now());
        });
    }

    @Override
    public ActiveConfig disableInstitution(String instId) {
        String id = normalizeInstitutionId(instId);
        if (id.isBlank()) {
            return getActiveConfig();
        }
        return update(cfg -> {
            Set<String> enabled = new LinkedHashSet<>(cfg.enabledInstitutions());
            Set<String> disabled = new LinkedHashSet<>(cfg.disabledInstitutions());
            enabled.remove(id);
            disabled.add(id);
            return new ActiveConfig(cfg.enabledKeywords(), cfg.disabledKeywords(), List.copyOf(enabled), List.copyOf(disabled), Instant.now());
        });
    }

    private ActiveConfig update(java.util.function.UnaryOperator<ActiveConfig> mutator) {
        ActiveConfig current = getActiveConfig();
        ActiveConfig updated = mutator.apply(current);
        persist(updated);
        return updated;
    }

    private void persist(ActiveConfig cfg) {
        try {
            Map<String, Object> doc = Map.of(
                    "enabled_keywords", cfg.enabledKeywords(),
                    "disabled_keywords", cfg.disabledKeywords(),
                    "enabled_institutions", cfg.enabledInstitutions(),
                    "disabled_institutions", cfg.disabledInstitutions(),
                    "updated_at", cfg.updatedAt().toString()
            );
            client.index(i -> i.index(INDEX).id(ID).document(doc));
        } catch (Exception e) {
            log.error("Failed to persist active_config.", e);
        }
    }

    private ActiveConfig seedIfMissing() {
        ActiveConfig seeded = new ActiveConfig(List.of(), List.of(), List.of(), List.of(), Instant.now());
        persist(seeded);
        return seeded;
    }

    private ActiveConfig fromSource(Map<?, ?> source) {
        List<String> enabledKeywords = asStringList(source.get("enabled_keywords")).stream().map(KeywordNormalizeUtil::normalize).filter(s -> !s.isBlank()).distinct().toList();
        List<String> disabledKeywords = asStringList(source.get("disabled_keywords")).stream().map(KeywordNormalizeUtil::normalize).filter(s -> !s.isBlank()).distinct().toList();
        List<String> enabledInstitutions = asStringList(source.get("enabled_institutions")).stream()
                .map(this::normalizeInstitutionId)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
        List<String> disabledInstitutions = asStringList(source.get("disabled_institutions")).stream()
                .map(this::normalizeInstitutionId)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
        Instant updatedAt = parseInstant(source.get("updated_at"));
        return new ActiveConfig(enabledKeywords, disabledKeywords, enabledInstitutions, disabledInstitutions, updatedAt);
    }

    private String normalizeInstitutionId(String raw) {
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

    private List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o == null) continue;
                out.add(String.valueOf(o));
            }
            return out;
        }
        return List.of();
    }

    private Instant parseInstant(Object value) {
        if (value == null) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(String.valueOf(value));
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }
}
