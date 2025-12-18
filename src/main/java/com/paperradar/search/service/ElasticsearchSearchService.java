package com.paperradar.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.paperradar.search.dto.SearchRequest;
import com.paperradar.search.model.SearchResultPage;
import com.paperradar.search.model.WorkSummary;
import com.paperradar.util.KeywordNormalizeUtil;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ElasticsearchSearchService implements SearchService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchSearchService.class);

    private final ElasticsearchClient client;

    @Override
    public SearchResultPage search(@Valid SearchRequest request) {
        int size = request.size();
        int page = request.page();
        int from = Math.max(0, (page - 1) * size);

        Query query = buildQuery(request);
        List<SortOptions> sortOptions = buildSort(request.sort());

        try {
            SearchResponse<Map> response = client.search(s -> {
                        s.index("works");
                        s.from(from);
                        s.size(size);
                        s.trackTotalHits(t -> t.enabled(true));
                        s.query(query);
                        if (!sortOptions.isEmpty()) {
                            s.sort(sortOptions);
                        }
                        s.source(src -> src.filter(f -> f.includes(
                                "title",
                                "publication_date",
                                "institutions",
                                "best_link_type",
                                "best_link_url"
                        )));
                        return s;
                    },
                    Map.class
            );

            long total = response.hits().total() == null ? 0 : response.hits().total().value();
            List<WorkSummary> items = response.hits().hits().stream()
                    .map(hit -> toSummary(hit.id(), (Map<?, ?>) hit.source()))
                    .filter(Objects::nonNull)
                    .toList();

            return new SearchResultPage(items, total, page, size);
        } catch (Exception e) {
            log.error("Elasticsearch search failed.", e);
            return new SearchResultPage(List.of(), 0, page, size);
        }
    }

    private Query buildQuery(SearchRequest request) {
        List<Query> must = new ArrayList<>();
        List<Query> filter = new ArrayList<>();

        if (request.q() != null && !request.q().isBlank()) {
            must.add(Query.of(q -> q.multiMatch(mm -> mm
                    .query(request.q())
                    .fields(
                            "title",
                            "abstract",
                            "keywords",
                            "keyword_candidates",
                            "authors.name",
                            "institutions.name"
                    )
                    .operator(Operator.And)
            )));
        }

        if (request.keyword() != null && !request.keyword().isBlank()) {
            String normalizedKeyword = KeywordNormalizeUtil.normalize(request.keyword());
            if (!normalizedKeyword.isBlank()) {
                filter.add(Query.of(q -> q.term(t -> t.field("keywords").value(normalizedKeyword))));
            }
        }

        if (request.instId() != null && !request.instId().isBlank()) {
            List<String> instIds = normalizeInstitutionIds(request.instId());
            if (instIds.size() == 1) {
                filter.add(Query.of(q -> q.term(t -> t.field("institutions.id").value(instIds.getFirst()))));
            } else if (!instIds.isEmpty()) {
                filter.add(Query.of(q -> q.bool(b -> {
                    instIds.forEach(id -> b.should(q2 -> q2.term(t -> t.field("institutions.id").value(id))));
                    b.minimumShouldMatch("1");
                    return b;
                })));
            }
        }

        if (request.author() != null && !request.author().isBlank()) {
            filter.add(Query.of(q -> q.nested(n -> n
                    .path("authors")
                    .query(q2 -> q2.bool(b -> b
                            .should(q3 -> q3.term(t -> t.field("authors.name").value(request.author())))
                            .should(q3 -> q3.prefix(p -> p.field("authors.name").value(request.author().trim())))
                            .minimumShouldMatch("1")
                    ))
            )));
        }

        boolean hasDateFilter = false;
        LocalDate from = request.from();
        if (from != null) {
            hasDateFilter = true;
        }
        LocalDate to = request.to();
        if (to != null) {
            hasDateFilter = true;
        }
        if (hasDateFilter) {
            filter.add(Query.of(q -> q.range(r -> r.date(dr -> {
                dr.field("publication_date");
                if (from != null) {
                    dr.gte(from.toString());
                }
                if (to != null) {
                    dr.lte(to.toString());
                }
                return dr;
            }))));
        }

        if (must.isEmpty() && filter.isEmpty()) {
            return Query.of(q -> q.matchNone(mn -> mn));
        }

        return Query.of(q -> q.bool(b -> b.must(must).filter(filter)));
    }

    private List<String> normalizeInstitutionIds(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isBlank()) {
            return List.of();
        }

        Set<String> out = new LinkedHashSet<>();
        out.add(trimmed);

        String tail = toOpenAlexTailId(trimmed);
        if (!tail.isBlank()) {
            out.add(tail);
            out.add("https://openalex.org/" + tail);
        }

        if (looksLikeOpenAlexTailId(trimmed)) {
            out.add("https://openalex.org/" + trimmed);
        }

        return out.stream().filter(s -> s != null && !s.isBlank()).toList();
    }

    private boolean looksLikeOpenAlexTailId(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.length() < 2) return false;
        if (t.charAt(0) != 'I') return false;
        return Character.isDigit(t.charAt(1));
    }

    private String toOpenAlexTailId(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.startsWith("https://openalex.org/")) {
            return t.substring("https://openalex.org/".length());
        }
        if (t.startsWith("http://openalex.org/")) {
            return t.substring("http://openalex.org/".length());
        }
        return "";
    }

    private List<SortOptions> buildSort(SearchRequest.Sort sort) {
        if (sort == null) {
            return List.of();
        }
        return switch (sort) {
            case newest -> List.of(SortOptions.of(s -> s
                    .field(f -> f.field("publication_date").order(SortOrder.Desc))
            ));
            case cited -> List.of(SortOptions.of(s -> s
                    .field(f -> f.field("cited_by_count").order(SortOrder.Desc))
            ));
            case relevance -> List.of();
        };
    }

    private WorkSummary toSummary(String id, Map<?, ?> source) {
        if (source == null) {
            return null;
        }

        String title = asString(source.get("title"));
        LocalDate publicationDate = parseDate(asString(source.get("publication_date")));
        String institutions = extractInstitutionNames(source.get("institutions"));
        String bestLinkType = asString(source.get("best_link_type"));
        String bestLinkUrl = asString(source.get("best_link_url"));

        return new WorkSummary(id, title, institutions, publicationDate, bestLinkType, bestLinkUrl);
    }

    private String extractInstitutionNames(Object institutionsValue) {
        if (!(institutionsValue instanceof List<?> list)) {
            return "";
        }

        Set<String> names = new LinkedHashSet<>();
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> m)) {
                continue;
            }
            String name = asString(m.get("name"));
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return String.join(", ", names);
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
