package com.paperradar.suggest.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.paperradar.suggest.model.SuggestItem;
import com.paperradar.util.RegexEscapeUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ElasticsearchSuggestService implements SuggestService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchSuggestService.class);

    private final ElasticsearchClient client;

    @Override
    public List<SuggestItem> suggestKeywords(String prefix, int size) {
        String regex = "^" + RegexEscapeUtil.escapeForElasticsearchRegex(prefix) + ".*";
        try {
            SearchResponse<Void> response = client.search(s -> s
                            .index("works")
                            .size(0)
                            .aggregations("keywords", a -> a
                                    .terms(t -> t
                                            .field("keyword_candidates")
                                            .include(i -> i.regexp(regex))
                                            .size(size)
                                    )
                            ),
                    Void.class
            );
            return termsToItems(response.aggregations().get("keywords"));
        } catch (Exception e) {
            log.warn("Keyword suggest failed.", e);
            return List.of();
        }
    }

    @Override
    public List<SuggestItem> suggestInstitutions(String prefix, int size) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }

        try {
            SearchResponse<Map> response = client.search(s -> s
                            .index("institutions")
                            .size(size)
                            .query(q -> q.matchPhrasePrefix(mpp -> mpp.field("display_name").query(prefix)))
                            .source(src -> src.filter(f -> f.includes("id", "display_name"))),
                    Map.class
            );

            return response.hits().hits().stream()
                    .map(h -> (Map<?, ?>) h.source())
                    .filter(Objects::nonNull)
                    .map(src -> new SuggestItem(asString(src.get("id")), asString(src.get("display_name"))))
                    .filter(it -> it.value() != null && !it.value().isBlank())
                    .toList();
        } catch (Exception e) {
            log.warn("Institution suggest failed.", e);
            return List.of();
        }
    }

    @Override
    public List<SuggestItem> suggestAuthors(String prefix, int size) {
        String regex = "^" + RegexEscapeUtil.escapeForElasticsearchRegex(prefix) + ".*";
        try {
            SearchResponse<Void> response = client.search(s -> s
                            .index("works")
                            .size(0)
                            .aggregations("authors", a -> a
                                    .nested(n -> n.path("authors"))
                                    .aggregations("names", a2 -> a2
                                            .terms(t -> t
                                                    .field("authors.name")
                                                    .include(i -> i.regexp(regex))
                                                    .size(size)
                                            )
                                    )
                            ),
                    Void.class
            );

            Aggregate nested = response.aggregations().get("authors");
            if (nested == null || nested.nested() == null) {
                return List.of();
            }
            return termsToItems(nested.nested().aggregations().get("names"));
        } catch (Exception e) {
            log.warn("Author suggest failed.", e);
            return List.of();
        }
    }

    private List<SuggestItem> termsToItems(Aggregate agg) {
        if (agg == null || agg.sterms() == null) {
            return List.of();
        }
        List<SuggestItem> items = new ArrayList<>();
        for (StringTermsBucket b : agg.sterms().buckets().array()) {
            String key = toStringKey(b.key());
            if (key == null || key.isBlank()) {
                continue;
            }
            items.add(new SuggestItem(key, key));
        }
        return items;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
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
