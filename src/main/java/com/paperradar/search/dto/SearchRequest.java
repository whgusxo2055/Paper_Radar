package com.paperradar.search.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record SearchRequest(
        String q,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        String keyword,
        String instId,
        String author,
        Sort sort,
        @Min(1) Integer page,
        @Min(1) @Max(50) Integer size
) {
    public SearchRequest {
        if (sort == null) {
            sort = Sort.relevance;
        }
        if (page == null) {
            page = 1;
        }
        if (size == null) {
            size = 10;
        }
    }

    public boolean isEmptyQuery() {
        return (q == null || q.isBlank())
                && (keyword == null || keyword.isBlank())
                && (instId == null || instId.isBlank())
                && (author == null || author.isBlank())
                && from == null
                && to == null;
    }

    public enum Sort {
        relevance,
        newest,
        cited
    }
}

