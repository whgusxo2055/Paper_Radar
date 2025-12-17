package com.paperradar.ingest.model;

import java.time.Instant;

public record IngestJob(
        String jobId,
        IngestMode mode,
        IngestStatus status,
        Instant startedAt,
        Instant endedAt,
        int processedCount,
        int createdCount,
        int updatedCount,
        String errorSummary
) {}

