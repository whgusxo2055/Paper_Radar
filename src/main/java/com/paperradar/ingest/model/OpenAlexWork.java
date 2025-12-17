package com.paperradar.ingest.model;

import java.time.LocalDate;
import java.util.List;

public record OpenAlexWork(
        String id,
        String doi,
        String landingPageUrl,
        String pdfUrl,
        String openAccessUrl,
        String title,
        String abstractText,
        LocalDate publicationDate,
        int citedByCount,
        List<String> keywords,
        List<AuthorRef> authors,
        List<InstitutionRef> institutions
) {
    public record AuthorRef(String id, String name) {}

    public record InstitutionRef(String id, String name) {}
}
