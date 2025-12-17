package com.paperradar.admin.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.paperradar.admin.model.ActiveConfig;
import com.paperradar.util.KeywordNormalizeUtil;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ElasticsearchKeywordAdminService implements KeywordAdminService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchKeywordAdminService.class);

    private final ElasticsearchClient client;
    private final ConfigService configService;

    @Override
    public List<String> suggestCandidates(int lookbackDays, int size) {
        ActiveConfig cfg = configService.getActiveConfig();
        Set<String> excluded = new LinkedHashSet<>();
        excluded.addAll(cfg.enabledKeywords());
        excluded.addAll(cfg.disabledKeywords());

        LocalDate from = LocalDate.now().minusDays(Math.max(1, lookbackDays));

        try {
            SearchResponse<Void> response = client.search(s -> s
                            .index("works")
                            .size(0)
                            .query(q -> q.range(r -> r.date(dr -> dr.field("publication_date").gte(from.toString()))))
                            .aggregations("candidates", a -> a
                                    .terms(t -> t.field("keyword_candidates").size(size))
                            ),
                    Void.class
            );

            Aggregate agg = response.aggregations().get("candidates");
            if (agg == null || agg.sterms() == null) {
                return List.of();
            }

            Set<String> out = new LinkedHashSet<>();
            for (StringTermsBucket b : agg.sterms().buckets().array()) {
                String key = toStringKey(b.key());
                String normalized = KeywordNormalizeUtil.normalize(key);
                if (normalized.isBlank()) {
                    continue;
                }
                if (excluded.contains(normalized)) {
                    continue;
                }
                out.add(normalized);
            }
            return out.stream().limit(size).toList();
        } catch (Exception e) {
            log.warn("Keyword candidates query failed.", e);
            return List.of();
        }
    }

    @Override
    public ActiveConfig config() {
        return configService.getActiveConfig();
    }

    private String toStringKey(co.elastic.clients.elasticsearch._types.FieldValue key) {
        if (key == null) {
            return null;
        }
        if (key.isString()) {
            return key.stringValue();
        }
        return key._toJsonString();
    }
}

