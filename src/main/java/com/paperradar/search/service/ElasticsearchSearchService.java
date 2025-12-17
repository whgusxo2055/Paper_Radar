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
            filter.add(Query.of(q -> q.term(t -> t.field("keywords").value(request.keyword()))));
        }

        if (request.instId() != null && !request.instId().isBlank()) {
            filter.add(Query.of(q -> q.term(t -> t.field("institutions.id").value(request.instId()))));
        }

        if (request.author() != null && !request.author().isBlank()) {
            filter.add(Query.of(q -> q.nested(n -> n
                    .path("authors")
                    .query(q2 -> q2.term(t -> t.field("authors.name").value(request.author())))
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
