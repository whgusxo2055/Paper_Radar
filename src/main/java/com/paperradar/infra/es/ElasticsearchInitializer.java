package com.paperradar.infra.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "paperradar.es.init.enabled", matchIfMissing = true)
public class ElasticsearchInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchInitializer.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final Duration maxWait;
    private final Duration pollInterval;

    public ElasticsearchInitializer(
            ObjectMapper objectMapper,
            @Value("${ELASTICSEARCH_URL:${elasticsearch.url:http://localhost:9200}}") String elasticsearchUrl,
            @Value("${paperradar.es.init.max-wait-seconds:60}") long maxWaitSeconds,
            @Value("${paperradar.es.init.poll-interval-millis:2000}") long pollIntervalMillis
    ) {
        this.objectMapper = objectMapper;
        this.baseUri = URI.create(elasticsearchUrl.endsWith("/") ? elasticsearchUrl.substring(0, elasticsearchUrl.length() - 1) : elasticsearchUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.maxWait = Duration.ofSeconds(Math.max(maxWaitSeconds, 0));
        this.pollInterval = Duration.ofMillis(Math.max(pollIntervalMillis, 100));
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Elasticsearch init runner started: baseUri={}, maxWaitSeconds={}, pollIntervalMillis={}", baseUri, maxWait.toSeconds(), pollInterval.toMillis());

        if (!waitUntilReady()) {
            log.warn("Elasticsearch not reachable/ready at {} within {}s. Skipping index initialization.", baseUri, maxWait.toSeconds());
            return;
        }

        try {
            ensureIndex("works", EsMappings.works());
            ensureIndex("institutions", EsMappings.institutions());
            ensureIndex("keyword_configs", EsMappings.keywordConfigs());
            ensureIndex("ingest_jobs", EsMappings.ingestJobs());
            ensureIndex("maintenance_jobs", EsMappings.maintenanceJobs());
            ensureKeywordConfigSeed();
            log.info("Elasticsearch init runner finished.");
        } catch (Exception e) {
            log.error("Failed to initialize Elasticsearch indices/documents.", e);
        }
    }

    private boolean waitUntilReady() {
        Instant deadline = Instant.now().plus(maxWait);
        int attempt = 0;
        while (Instant.now().isBefore(deadline)) {
            attempt++;
            if (isReadyOnce()) {
                if (attempt > 1) {
                    log.info("Elasticsearch became ready after {} attempt(s).", attempt);
                }
                return true;
            }
            try {
                Thread.sleep(pollInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return isReadyOnce();
    }

    private boolean isReadyOnce() {
        try {
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/_cluster/health?wait_for_status=yellow&timeout=1s"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }

    private void ensureIndex(String indexName, Map<String, Object> body) throws Exception {
        if (indexExists(indexName)) {
            return;
        }

        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/" + indexName))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.info("Created index: {}", indexName);
            return;
        }

        if (response.statusCode() == 400 && response.body() != null
                && response.body().toLowerCase(Locale.ROOT).contains("resource_already_exists_exception")) {
            log.info("Index {} already exists (race).", indexName);
            return;
        }

        log.warn("Failed to create index {} (status={}): {}", indexName, response.statusCode(), response.body());
    }

    private boolean indexExists(String indexName) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/" + indexName))
                .timeout(Duration.ofSeconds(5))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        return response.statusCode() == 200;
    }

    private void ensureKeywordConfigSeed() throws Exception {
        String indexName = "keyword_configs";
        String id = "active_config";

        if (!indexExists(indexName)) {
            return;
        }

        if (documentExists(indexName, id)) {
            return;
        }

        Map<String, Object> seed = new LinkedHashMap<>();
        seed.put("enabled_keywords", new String[] {});
        seed.put("disabled_keywords", new String[] {});
        seed.put("enabled_institutions", new String[] {});
        seed.put("disabled_institutions", new String[] {});
        seed.put("updated_at", Instant.EPOCH.toString());

        String json = objectMapper.writeValueAsString(seed);
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/" + indexName + "/_doc/" + id))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.info("Created seed document: {}/_doc/{}", indexName, id);
            return;
        }

        log.warn(
                "Failed to create seed document {}/_doc/{} (status={}): {}",
                indexName,
                id,
                response.statusCode(),
                response.body()
        );
    }

    private boolean documentExists(String indexName, String id) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/" + indexName + "/_doc/" + id))
                .timeout(Duration.ofSeconds(5))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        return response.statusCode() == 200;
    }
}
