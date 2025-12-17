package com.paperradar.trend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paperradar.admin.model.ActiveConfig;
import com.paperradar.admin.model.InstitutionSummary;
import com.paperradar.admin.service.ConfigService;
import com.paperradar.admin.service.InstitutionService;
import com.paperradar.trend.model.TrendItem;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ElasticsearchTrendService implements TrendService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchTrendService.class);

    private final ObjectMapper objectMapper;
    private final ConfigService configService;
    private final InstitutionService institutionService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    @Value("${ELASTICSEARCH_URL:${elasticsearch.url:http://localhost:9200}}")
    private String elasticsearchUrl;

    @Override
    public List<TrendItem> keywordTrends(int topN) {
        ActiveConfig cfg = configService.getActiveConfig();
        if (cfg.enabledKeywords().isEmpty()) {
            return List.of();
        }

        List<String> include = cfg.enabledKeywords();
        List<TrendItem> items = runTrendAgg(
                "keywords",
                "keywords",
                include,
                topN
        );

        return sortAndLimit(items, topN);
    }

    @Override
    public List<TrendItem> institutionTrends(int topN) {
        ActiveConfig cfg = configService.getActiveConfig();
        if (cfg.enabledInstitutions().isEmpty()) {
            return List.of();
        }

        List<String> include = cfg.enabledInstitutions();
        Map<String, String> idToName = new HashMap<>();
        for (InstitutionSummary it : institutionService.getByIds(include)) {
            if (it.id() == null || it.id().isBlank()) continue;
            idToName.put(it.id(), (it.displayName() == null || it.displayName().isBlank()) ? it.id() : it.displayName());
        }

        List<TrendItem> raw = runTrendAgg(
                "institutions",
                "institutions.id",
                include,
                topN
        );

        List<TrendItem> labeled = raw.stream()
                .map(it -> new TrendItem(
                        it.key(),
                        idToName.getOrDefault(it.key(), it.key()),
                        it.trendScore(),
                        it.ma7(),
                        it.ma30(),
                        it.total30()
                ))
                .toList();

        return sortAndLimit(labeled, topN);
    }

    private List<TrendItem> runTrendAgg(String aggName, String field, List<String> include, int topN) {
        try {
            URI base = toBaseUri(elasticsearchUrl);
            String body = objectMapper.writeValueAsString(trendQuery(field, include, Math.max(topN, include.size())));

            HttpRequest request = HttpRequest.newBuilder(base.resolve("/works/_search"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Trend query failed (status={}): {}", response.statusCode(), response.body());
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode buckets = root.path("aggregations").path(aggName).path("buckets");
            if (!buckets.isArray()) {
                return List.of();
            }

            List<TrendItem> out = new ArrayList<>();
            for (JsonNode b : buckets) {
                String key = b.path("key").asText("");
                if (key.isBlank()) continue;

                JsonNode days = b.path("per_day").path("buckets");
                TrendNumbers nums = extractLastValues(days);
                out.add(new TrendItem(key, key, nums.trendScore, nums.ma7, nums.ma30, nums.total30));
            }
            return out;
        } catch (Exception e) {
            log.warn("Trend query error.", e);
            return List.of();
        }
    }

    private Map<String, Object> trendQuery(String field, List<String> includeTerms, int bucketSize) {
        Map<String, Object> range = Map.of("range", Map.of(
                "publication_date", Map.of("gte", "now-90d/d", "lte", "now/d")
        ));

        Map<String, Object> query = Map.of("bool", Map.of("filter", List.of(range)));

        Map<String, Object> include = Map.of("include", includeTerms);
        Map<String, Object> terms = new HashMap<>();
        terms.put("field", field);
        terms.put("size", bucketSize);
        terms.putAll(include);

        Map<String, Object> dateHistogram = Map.of(
                "field", "publication_date",
                "calendar_interval", "day",
                "min_doc_count", 0,
                "extended_bounds", Map.of("min", "now-90d/d", "max", "now/d")
        );

        Map<String, Object> perDayAggs = Map.of(
                "ma7", Map.of("moving_fn", Map.of(
                        "buckets_path", "_count",
                        "window", 7,
                        "script", "MovingFunctions.unweightedAvg(values)"
                )),
                "ma30", Map.of("moving_fn", Map.of(
                        "buckets_path", "_count",
                        "window", 30,
                        "script", "MovingFunctions.unweightedAvg(values)"
                )),
                "total30", Map.of("moving_fn", Map.of(
                        "buckets_path", "_count",
                        "window", 30,
                        "script", "MovingFunctions.sum(values)"
                )),
                "trend_score", Map.of("bucket_script", Map.of(
                        "buckets_path", Map.of("ma7", "ma7", "ma30", "ma30"),
                        "script", "(params.ma7 - params.ma30) / Math.max(params.ma30, 1)"
                ))
        );

        Map<String, Object> perDay = Map.of(
                "date_histogram", dateHistogram,
                "aggs", perDayAggs
        );

        Map<String, Object> by = Map.of(
                "terms", terms,
                "aggs", Map.of("per_day", perDay)
        );

        return Map.of(
                "size", 0,
                "query", query,
                "aggs", Map.of(
                        field.equals("keywords") ? "keywords" : "institutions", by
                )
        );
    }

    private record TrendNumbers(double trendScore, double ma7, double ma30, double total30) {}

    private TrendNumbers extractLastValues(JsonNode dayBuckets) {
        double lastTrend = 0;
        double lastMa7 = 0;
        double lastMa30 = 0;
        double lastTotal30 = 0;
        boolean hasTrend = false;

        if (!dayBuckets.isArray()) {
            return new TrendNumbers(0, 0, 0, 0);
        }

        for (JsonNode day : dayBuckets) {
            Double ma7 = numberValue(day.path("ma7").path("value"));
            Double ma30 = numberValue(day.path("ma30").path("value"));
            Double total30 = numberValue(day.path("total30").path("value"));
            Double trend = numberValue(day.path("trend_score").path("value"));

            if (ma7 != null) lastMa7 = ma7;
            if (ma30 != null) lastMa30 = ma30;
            if (total30 != null) lastTotal30 = total30;
            if (trend != null) {
                lastTrend = trend;
                hasTrend = true;
            }
        }

        if (!hasTrend) {
            lastTrend = 0;
        }
        return new TrendNumbers(lastTrend, lastMa7, lastMa30, lastTotal30);
    }

    private Double numberValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isNumber()) {
            return null;
        }
        return node.asDouble();
    }

    private List<TrendItem> sortAndLimit(List<TrendItem> items, int topN) {
        Comparator<TrendItem> cmp = Comparator
                .comparingDouble(TrendItem::trendScore).reversed()
                .thenComparingDouble(TrendItem::ma7).reversed()
                .thenComparingDouble(TrendItem::total30).reversed();
        return items.stream()
                .filter(Objects::nonNull)
                .sorted(cmp)
                .limit(topN)
                .toList();
    }

    private URI toBaseUri(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isBlank()) {
            return URI.create("http://localhost:9200");
        }
        return URI.create(trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed);
    }
}

