package com.paperradar.ingest.openalex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paperradar.ingest.model.OpenAlexWork;
import com.paperradar.util.KeywordNormalizeUtil;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HttpOpenAlexClient implements OpenAlexClient {

    private static final Logger log = LoggerFactory.getLogger(HttpOpenAlexClient.class);

    private static final String BASE = "https://api.openalex.org";
    private static final int PER_PAGE = 200;
    private static final int MAX_PAGES = 50;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${OPENALEX_EMAIL:}")
    private String openAlexEmail;

    @Override
    public List<OpenAlexWork> fetchWorksByKeyword(String keyword, LocalDate fromPublicationDate, LocalDate fromUpdatedDate) {
        String normalized = KeywordNormalizeUtil.normalize(keyword);
        if (normalized.isBlank()) {
            return List.of();
        }
        String search = normalized;
        return fetchWorks(search, null, fromPublicationDate, fromUpdatedDate);
    }

    @Override
    public List<OpenAlexWork> fetchWorksByInstitution(String openAlexInstitutionId, LocalDate fromPublicationDate, LocalDate fromUpdatedDate) {
        String inst = openAlexInstitutionId == null ? "" : openAlexInstitutionId.trim();
        if (inst.isBlank()) {
            return List.of();
        }
        return fetchWorks(null, inst, fromPublicationDate, fromUpdatedDate);
    }

    private List<OpenAlexWork> fetchWorks(String search, String institutionId, LocalDate fromPublicationDate, LocalDate fromUpdatedDate) {
        List<OpenAlexWork> all = new ArrayList<>();
        String cursor = "*";

        for (int page = 0; page < MAX_PAGES; page++) {
            try {
                URI uri = buildUri(search, institutionId, fromPublicationDate, fromUpdatedDate, cursor);
                HttpRequest req = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(20))
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() < 200 || res.statusCode() >= 300) {
                    log.warn("OpenAlex request failed (status={}): {}", res.statusCode(), res.body());
                    break;
                }
                JsonNode root = objectMapper.readTree(res.body());
                all.addAll(OpenAlexJsonMapper.parseWorks(root));

                String next = root.path("meta").path("next_cursor").asText("");
                if (next.isBlank()) {
                    break;
                }
                cursor = next;
            } catch (Exception e) {
                log.warn("OpenAlex request error.", e);
                break;
            }
        }

        return all;
    }

    private URI buildUri(String search, String institutionId, LocalDate fromPublicationDate, LocalDate fromUpdatedDate, String cursor) {
        StringBuilder sb = new StringBuilder(BASE).append("/works?");
        sb.append("per-page=").append(PER_PAGE);
        sb.append("&cursor=").append(url(cursor));

        if (search != null && !search.isBlank()) {
            sb.append("&search=").append(url(search));
        }

        List<String> filters = new ArrayList<>();
        if (institutionId != null && !institutionId.isBlank()) {
            filters.add("institutions.id:" + institutionId);
        }
        if (fromPublicationDate != null) {
            filters.add("from_publication_date:" + fromPublicationDate);
        }
        if (fromUpdatedDate != null) {
            filters.add("from_updated_date:" + fromUpdatedDate);
        }
        if (!filters.isEmpty()) {
            sb.append("&filter=").append(url(String.join(",", filters)));
        }

        if (openAlexEmail != null && !openAlexEmail.isBlank()) {
            sb.append("&mailto=").append(url(openAlexEmail.trim()));
        }

        return URI.create(sb.toString());
    }

    private String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}

