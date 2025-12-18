package com.paperradar.ingest.model;

import java.time.Instant;
import java.time.LocalDate;

public record IngestJob(
        String jobId,
        IngestMode mode,
        IngestStatus status,
        Instant startedAt,
        Instant endedAt,
        Instant lastHeartbeatAt,
        Instant lastProgressAt,
        int processedCount,
        int createdCount,
        int updatedCount,
        String errorSummary,
        String currentSource,
        String currentKey,
        LocalDate fromPublicationDate,
        LocalDate toPublicationDate
) {}
