package com.paperradar.search.model;

import java.time.LocalDate;

public record WorkSummary(
        String id,
        String title,
        String institutions,
        LocalDate publicationDate,
        String bestLinkType,
        String bestLinkUrl
) {}
