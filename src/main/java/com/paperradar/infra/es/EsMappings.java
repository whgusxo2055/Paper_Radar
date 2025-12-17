package com.paperradar.infra.es;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class EsMappings {

    private EsMappings() {}

    static Map<String, Object> works() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("source", Map.of("type", "keyword"));
        properties.put("source_work_id", Map.of("type", "keyword"));
        properties.put("doi", Map.of("type", "keyword"));
        properties.put("landing_page_url", Map.of("type", "keyword"));
        properties.put("pdf_url", Map.of("type", "keyword"));
        properties.put("open_access_oa_url", Map.of("type", "keyword"));
        properties.put("best_link_url", Map.of("type", "keyword"));
        properties.put("best_link_type", Map.of("type", "keyword"));
        properties.put("title", textWithKeyword());
        properties.put("abstract", Map.of("type", "text"));
        properties.put("keywords", Map.of("type", "keyword"));
        properties.put("keyword_candidates", Map.of("type", "keyword"));
        properties.put("publication_date", Map.of("type", "date"));
        properties.put("cited_by_count", Map.of("type", "integer"));

        Map<String, Object> authorProperties = new LinkedHashMap<>();
        authorProperties.put("id", Map.of("type", "keyword"));
        authorProperties.put("name", Map.of("type", "keyword"));
        Map<String, Object> authors = new LinkedHashMap<>();
        authors.put("type", "nested");
        authors.put("properties", authorProperties);
        properties.put("authors", authors);

        Map<String, Object> instProperties = new LinkedHashMap<>();
        instProperties.put("id", Map.of("type", "keyword"));
        instProperties.put("name", textWithKeyword());
        properties.put("institutions", Map.of("type", "object", "properties", instProperties));

        properties.put("created_at", Map.of("type", "date"));
        properties.put("updated_at", Map.of("type", "date"));

        return indexBody(properties);
    }

    static Map<String, Object> institutions() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Map.of("type", "keyword"));
        properties.put("display_name", textWithKeyword());
        properties.put("name_aliases", Map.of("type", "keyword"));
        properties.put("active", Map.of("type", "boolean"));
        properties.put("updated_at", Map.of("type", "date"));
        return indexBody(properties);
    }

    static Map<String, Object> keywordConfigs() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("enabled_keywords", Map.of("type", "keyword"));
        properties.put("disabled_keywords", Map.of("type", "keyword"));
        properties.put("enabled_institutions", Map.of("type", "keyword"));
        properties.put("disabled_institutions", Map.of("type", "keyword"));
        properties.put("updated_at", Map.of("type", "date"));
        return indexBody(properties);
    }

    static Map<String, Object> ingestJobs() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("job_id", Map.of("type", "keyword"));
        properties.put("mode", Map.of("type", "keyword"));
        properties.put("status", Map.of("type", "keyword"));
        properties.put("started_at", Map.of("type", "date"));
        properties.put("ended_at", Map.of("type", "date"));
        properties.put("processed_count", Map.of("type", "integer"));
        properties.put("created_count", Map.of("type", "integer"));
        properties.put("updated_count", Map.of("type", "integer"));
        properties.put("error_summary", Map.of("type", "text"));
        return indexBody(properties);
    }

    static Map<String, Object> maintenanceJobs() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("job_id", Map.of("type", "keyword"));
        properties.put("type", Map.of("type", "keyword"));
        properties.put("status", Map.of("type", "keyword"));
        properties.put("started_at", Map.of("type", "date"));
        properties.put("ended_at", Map.of("type", "date"));
        properties.put("scanned_count", Map.of("type", "integer"));
        properties.put("updated_count", Map.of("type", "integer"));
        properties.put("failed_count", Map.of("type", "integer"));
        properties.put("failed_doc_ids", Map.of("type", "keyword"));
        properties.put("error_summary", Map.of("type", "text"));
        return indexBody(properties);
    }

    private static Map<String, Object> indexBody(Map<String, Object> properties) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("settings", Map.of(
                "number_of_shards", 1,
                "number_of_replicas", 0,
                "refresh_interval", "1s"
        ));
        body.put("mappings", Map.of(
                "dynamic", true,
                "properties", properties
        ));
        return body;
    }

    private static Map<String, Object> textWithKeyword() {
        return Map.of(
                "type", "text",
                "fields", Map.of("keyword", Map.of("type", "keyword", "ignore_above", 256))
        );
    }
}
