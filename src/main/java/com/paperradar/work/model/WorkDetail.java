package com.paperradar.work.model;

import com.paperradar.work.link.WorkLink;
import java.time.LocalDate;
import java.util.List;

public record WorkDetail(
        String id,
        String title,
        String abstractText,
        LocalDate publicationDate,
        String institutionsText,
        List<InstitutionRef> institutions,
        List<String> authors,
        List<String> keywords,
        List<WorkLink> links
) {
    public record InstitutionRef(String id, String name) {}
}
