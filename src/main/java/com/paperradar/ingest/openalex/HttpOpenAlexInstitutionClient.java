package com.paperradar.ingest.openalex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paperradar.ingest.openalex.OpenAlexInstitutionClient.OpenAlexInstitutionSummary;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HttpOpenAlexInstitutionClient implements OpenAlexInstitutionClient {

    private static final Logger log = LoggerFactory.getLogger(HttpOpenAlexInstitutionClient.class);

    private static final String BASE = "https://api.openalex.org";

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${OPENALEX_EMAIL:}")
    private String openAlexEmail;

    @Override
    public List<OpenAlexInstitutionSummary> searchInstitutions(String query, int size) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            return List.of();
        }
        int perPage = Math.min(Math.max(size, 1), 20);

        try {
            URI uri = buildSearchUri(q, perPage);
            JsonNode root = getJson(uri);
            JsonNode results = root.path("results");
            if (!results.isArray()) {
                return List.of();
            }
            List<OpenAlexInstitutionSummary> out = new ArrayList<>();
            for (JsonNode inst : results) {
                OpenAlexInstitutionSummary s = parse(inst);
                if (s != null) {
                    out.add(s);
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("OpenAlex institution search failed.", e);
            return List.of();
        }
    }

    @Override
    public OpenAlexInstitutionSummary getInstitution(String institutionId) {
        String id = normalizeInstitutionId(institutionId);
        if (id.isBlank()) {
            return null;
        }
        try {
            URI uri = buildGetUri(id);
            JsonNode root = getJson(uri);
            return parse(root);
        } catch (Exception e) {
            log.warn("OpenAlex institution get failed: {}", institutionId, e);
            return null;
        }
    }

    private URI buildSearchUri(String query, int perPage) {
        StringBuilder sb = new StringBuilder(BASE).append("/institutions?");
        sb.append("search=").append(url(query));
        sb.append("&per-page=").append(perPage);
        if (openAlexEmail != null && !openAlexEmail.isBlank()) {
            sb.append("&mailto=").append(url(openAlexEmail.trim()));
        }
        return URI.create(sb.toString());
    }

    private URI buildGetUri(String normalizedId) {
        StringBuilder sb = new StringBuilder(BASE).append("/institutions/").append(url(normalizedId));
        if (openAlexEmail != null && !openAlexEmail.isBlank()) {
            sb.append("?mailto=").append(url(openAlexEmail.trim()));
        }
        return URI.create(sb.toString());
    }

    private JsonNode getJson(URI uri) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            String body = res.body() == null ? "" : res.body();
            String snippet = body.length() > 500 ? body.substring(0, 500) + "..." : body;
            throw new IllegalStateException("OpenAlex request failed (status=%d, uri=%s): %s"
                    .formatted(res.statusCode(), uri, snippet));
        }
        return objectMapper.readTree(res.body());
    }

    private OpenAlexInstitutionSummary parse(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String id = text(node, "id");
        String name = text(node, "display_name");
        if (id.isBlank()) {
            return null;
        }
        return new OpenAlexInstitutionSummary(id, name == null ? "" : name);
    }

    private String normalizeInstitutionId(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isBlank()) {
            return "";
        }
        if (trimmed.startsWith("https://openalex.org/")) {
            return trimmed.substring("https://openalex.org/".length());
        }
        if (trimmed.startsWith("http://openalex.org/")) {
            return trimmed.substring("http://openalex.org/".length());
        }
        return trimmed;
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return "";
        return v.asText("");
    }

    private String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}

