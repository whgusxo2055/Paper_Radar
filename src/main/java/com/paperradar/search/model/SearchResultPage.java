package com.paperradar.search.model;

import java.util.List;

public record SearchResultPage(
        List<WorkSummary> items,
        long total,
        int page,
        int size
) {
    public int totalPages() {
        if (size <= 0) {
            return 0;
        }
        return (int) Math.max(1, (total + size - 1) / size);
    }
}

