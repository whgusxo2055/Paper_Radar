package com.paperradar.institution.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.FiltersBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.paperradar.admin.model.InstitutionSummary;
import com.paperradar.admin.service.InstitutionService;
import com.paperradar.institution.model.InstitutionAnalysis;
import com.paperradar.search.model.WorkSummary;
import java.time.LocalDate;
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
public class ElasticsearchInstitutionAnalysisService implements InstitutionAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchInstitutionAnalysisService.class);

    private final ElasticsearchClient client;
    private final InstitutionService institutionService;

    @Override
    public InstitutionAnalysis analyze(String institutionId, int recentSize, int topKeywordsSize, int keywordWindowDays) {
        String id = institutionId == null ? "" : institutionId.trim();
        if (id.isBlank()) {
            return new InstitutionAnalysis("", "", 0, 0, List.of(), List.of());
        }

        String displayName = resolveDisplayName(id);
        int window = (keywordWindowDays == 30) ? 30 : 90;

        try {
            Query instFilter = Query.of(q -> q.term(t -> t.field("institutions.id").value(id)));
            Query last30d = Query.of(q -> q.range(r -> r.date(dr -> dr.field("publication_date").gte("now-30d/d"))));
            Query last90d = Query.of(q -> q.range(r -> r.date(dr -> dr.field("publication_date").gte("now-90d/d"))));
            Query keywordWindowRange = Query.of(q -> q.range(r -> r.date(dr -> dr.field("publication_date").gte("now-" + window + "d/d"))));

            SearchResponse<Map> response = client.search(s -> {
                        s.index("works");
                        s.size(Math.min(Math.max(recentSize, 1), 50));
                        s.query(q -> q.bool(b -> b.filter(instFilter)));
                        s.sort(so -> so.field(f -> f.field("publication_date").order(SortOrder.Desc)));
                        s.source(src -> src.filter(f -> f.includes(
                                "title",
                                "publication_date",
                                "institutions",
                                "best_link_type",
                                "best_link_url"
                        )));
                        s.aggregations("counts", a -> a.filters(f -> f
                                .keyed(true)
                                .filters(fs -> fs.keyed(Map.of(
                                        "d30", last30d,
                                        "d90", last90d
                                )))
                        ));
                        s.aggregations("top_keywords", a -> a
                                .filter(f -> f.bool(b -> b.filter(instFilter).filter(keywordWindowRange)))
                                .aggregations("keywords", a2 -> a2
                                        .terms(t -> t.field("keywords").size(Math.min(Math.max(topKeywordsSize, 1), 50)))
                                )
                        );
                        return s;
                    },
                    Map.class);

            Counts counts = parseCounts(response.aggregations().get("counts"));
            List<String> topKeywords = parseKeywordTermsFromFilter(response.aggregations().get("top_keywords"));

            List<WorkSummary> works = response.hits().hits().stream()
                    .map(hit -> toSummary(hit.id(), (Map<?, ?>) hit.source()))
                    .filter(Objects::nonNull)
                    .toList();

            return new InstitutionAnalysis(id, displayName, counts.d30, counts.d90, topKeywords, works);
        } catch (Exception e) {
            log.warn("Institution analysis failed for {}", id, e);
            return new InstitutionAnalysis(id, displayName, 0, 0, List.of(), List.of());
        }
    }

    private String resolveDisplayName(String institutionId) {
        List<InstitutionSummary> list = institutionService.getByIds(List.of(institutionId));
        if (list.isEmpty()) {
            return institutionId;
        }
        InstitutionSummary it = list.getFirst();
        if (it.displayName() == null || it.displayName().isBlank()) {
            return institutionId;
        }
        return it.displayName();
    }

    private record Counts(long d30, long d90) {}

    private Counts parseCounts(Aggregate aggregate) {
        if (aggregate == null || aggregate.filters() == null || aggregate.filters().buckets() == null) {
            return new Counts(0, 0);
        }

        long d30 = 0;
        long d90 = 0;

        if (aggregate.filters().buckets().isKeyed()) {
            Map<String, FiltersBucket> keyed = aggregate.filters().buckets().keyed();
            d30 = keyed.containsKey("d30") ? keyed.get("d30").docCount() : 0;
            d90 = keyed.containsKey("d90") ? keyed.get("d90").docCount() : 0;
        }

        return new Counts(d30, d90);
    }

    private List<String> parseKeywordTermsFromFilter(Aggregate aggregate) {
        if (aggregate == null || aggregate.filter() == null) {
            return List.of();
        }
        Aggregate inner = aggregate.filter().aggregations().get("keywords");
        if (inner == null || inner.sterms() == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (StringTermsBucket b : inner.sterms().buckets().array()) {
            String key = toStringKey(b.key());
            if (key != null && !key.isBlank()) {
                out.add(key);
            }
        }
        return out;
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

        List<String> names = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> m)) {
                continue;
            }
            String name = asString(m.get("name"));
            if (name != null && !name.isBlank()) {
                if (!names.contains(name)) {
                    names.add(name);
                }
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
