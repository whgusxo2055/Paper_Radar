package com.paperradar.infra.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
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

    public ElasticsearchInitializer(
            ObjectMapper objectMapper,
            @Value("${ELASTICSEARCH_URL:${elasticsearch.url:http://localhost:9200}}") String elasticsearchUrl
    ) {
        this.objectMapper = objectMapper;
        this.baseUri = URI.create(elasticsearchUrl.endsWith("/") ? elasticsearchUrl.substring(0, elasticsearchUrl.length() - 1) : elasticsearchUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isReachable()) {
            log.warn("Elasticsearch not reachable at {}. Skipping index initialization.", baseUri);
            return;
        }

        try {
            ensureIndex("works", EsMappings.works());
            ensureIndex("institutions", EsMappings.institutions());
            ensureIndex("keyword_configs", EsMappings.keywordConfigs());
            ensureIndex("ingest_jobs", EsMappings.ingestJobs());
            ensureIndex("maintenance_jobs", EsMappings.maintenanceJobs());
            ensureKeywordConfigSeed();
        } catch (Exception e) {
            log.error("Failed to initialize Elasticsearch indices/documents.", e);
        }
    }

    private boolean isReachable() {
        try {
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 500;
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
        seed.put("updated_at", Instant.now().toString());

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
