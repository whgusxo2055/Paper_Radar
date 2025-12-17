package com.paperradar.ingest.service.enrich;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paperradar.ingest.model.OpenAlexWork;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(value = "paperradar.enrich.crossref.enabled", havingValue = "true")
public class CrossrefWorkLinkEnricher implements WorkLinkEnricher {

    private static final Logger log = LoggerFactory.getLogger(CrossrefWorkLinkEnricher.class);

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${OPENALEX_EMAIL:}")
    private String contactEmail;

    @Override
    public OpenAlexWork enrich(OpenAlexWork work) {
        if (work == null) return null;
        if (!needsEnrichment(work)) return work;
        if (work.doi() == null || work.doi().isBlank()) return work;

        try {
            URI uri = URI.create("https://api.crossref.org/works/" + url(work.doi()));
            HttpRequest.Builder b = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json");
            if (contactEmail != null && !contactEmail.isBlank()) {
                b.header("User-Agent", "PAPER_RADAR (mailto:" + contactEmail.trim() + ")");
            }
            HttpResponse<String> res = httpClient.send(b.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                return work;
            }

            JsonNode message = objectMapper.readTree(res.body()).path("message");
            JsonNode links = message.path("link");
            if (!links.isArray()) {
                return work;
            }

            String pdf = null;
            String landing = null;
            List<String> candidates = new ArrayList<>();
            for (JsonNode l : links) {
                String url = l.path("URL").asText("");
                String ct = l.path("content-type").asText("").toLowerCase();
                if (url.isBlank()) continue;
                candidates.add(url);
                if (pdf == null && (ct.contains("pdf") || url.toLowerCase().endsWith(".pdf"))) {
                    pdf = url;
                }
                if (landing == null && (ct.contains("html") || ct.contains("text"))) {
                    landing = url;
                }
            }
            if (landing == null && !candidates.isEmpty()) {
                landing = candidates.getFirst();
            }

            if (pdf == null && landing == null) {
                return work;
            }

            return new OpenAlexWork(
                    work.id(),
                    work.doi(),
                    work.landingPageUrl() == null ? landing : work.landingPageUrl(),
                    work.pdfUrl() == null ? pdf : work.pdfUrl(),
                    work.openAccessUrl(),
                    work.title(),
                    work.abstractText(),
                    work.publicationDate(),
                    work.citedByCount(),
                    work.keywords(),
                    work.authors(),
                    work.institutions()
            );
        } catch (Exception e) {
            log.debug("Crossref enrichment failed.", e);
            return work;
        }
    }

    private boolean needsEnrichment(OpenAlexWork work) {
        return (work.landingPageUrl() == null || work.landingPageUrl().isBlank())
                || (work.pdfUrl() == null || work.pdfUrl().isBlank());
    }

    private String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}

