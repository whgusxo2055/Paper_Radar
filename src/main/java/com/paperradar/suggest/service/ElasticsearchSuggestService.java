package com.paperradar.suggest.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.paperradar.ingest.openalex.OpenAlexInstitutionClient;
import com.paperradar.suggest.model.SuggestItem;
import com.paperradar.util.RegexEscapeUtil;
import com.paperradar.util.KeywordNormalizeUtil;
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
    private final OpenAlexInstitutionClient openAlexInstitutionClient;

    @Override
    public List<SuggestItem> suggestKeywords(String prefix, int size) {
        String normalized = KeywordNormalizeUtil.normalize(prefix);
        if (normalized.isBlank()) {
            return List.of();
        }
        String regex = RegexEscapeUtil.escapeForElasticsearchRegex(normalized) + ".*";
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
            String trimmed = prefix.trim();
            String openAlexUrlPrefix = trimmed.startsWith("I") ? "https://openalex.org/" + trimmed : null;
            SearchResponse<Map> response = client.search(s -> s
                            .index("institutions")
                            .size(size)
                            .query(q -> q.bool(b -> {
                                b.should(q1 -> q1.matchPhrasePrefix(mpp -> mpp.field("display_name").query(trimmed)));
                                b.should(q2 -> q2.prefix(p -> p.field("name_aliases").value(trimmed)));
                                b.should(q3 -> q3.prefix(p -> p.field("id").value(trimmed)));
                                if (openAlexUrlPrefix != null) {
                                    b.should(q4 -> q4.prefix(p -> p.field("id").value(openAlexUrlPrefix)));
                                }
                                b.minimumShouldMatch("1");
                                return b;
                            }))
                            .source(src -> src.filter(f -> f.includes("id", "display_name"))),
                    Map.class
            );

            List<SuggestItem> local = response.hits().hits().stream()
                    .map(h -> (Map<?, ?>) h.source())
                    .filter(Objects::nonNull)
                    .map(src -> {
                        String id = asString(src.get("id"));
                        String name = asString(src.get("display_name"));
                        if (name == null || name.isBlank()) {
                            name = fallbackInstitutionName(id);
                        }
                        return new SuggestItem(id, name);
                    })
                    .filter(it -> it.value() != null && !it.value().isBlank())
                    .toList();
            if (!local.isEmpty()) {
                return local;
            }

            // Fallback: OpenAlex 기관명 검색 (institutions 인덱스에 이름이 없거나 비어있는 초기 상태 대응)
            if (trimmed.length() >= 2 && openAlexInstitutionClient != null && !looksLikeInstitutionId(trimmed)) {
                return openAlexInstitutionClient.searchInstitutions(trimmed, size).stream()
                        .map(it -> new SuggestItem(
                                it.id(),
                                it.displayName() == null || it.displayName().isBlank()
                                        ? fallbackInstitutionName(it.id())
                                        : it.displayName()
                        ))
                        .filter(it -> it.value() != null && !it.value().isBlank())
                        .toList();
            }
            return List.of();
        } catch (Exception e) {
            log.warn("Institution suggest failed.", e);
            return List.of();
        }
    }

    @Override
    public List<SuggestItem> suggestAuthors(String prefix, int size) {
        String regex = toAsciiCaseInsensitivePrefixRegex(prefix);
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

    private String toAsciiCaseInsensitivePrefixRegex(String rawPrefix) {
        if (rawPrefix == null || rawPrefix.isBlank()) {
            return "^(?!)";
        }
        String trimmed = rawPrefix.trim();
        StringBuilder sb = new StringBuilder(trimmed.length() * 4);
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c >= 'a' && c <= 'z') {
                sb.append('[').append(c).append((char) (c - 32)).append(']');
                continue;
            }
            if (c >= 'A' && c <= 'Z') {
                sb.append('[').append((char) (c + 32)).append(c).append(']');
                continue;
            }
            sb.append(RegexEscapeUtil.escapeForElasticsearchRegex(String.valueOf(c)));
        }
        sb.append(".*");
        return sb.toString();
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

    private String fallbackInstitutionName(String id) {
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

    private boolean looksLikeInstitutionId(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.isBlank()) return false;
        if (t.startsWith("https://openalex.org/I") || t.startsWith("http://openalex.org/I")) return true;
        if (t.length() >= 2 && t.charAt(0) == 'I' && Character.isDigit(t.charAt(1))) return true;
        return false;
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
