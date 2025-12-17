package com.paperradar.admin.maintenance.model;

import java.time.Instant;
import java.util.List;

public record MaintenanceJob(
        String jobId,
        MaintenanceJobType type,
        MaintenanceJobStatus status,
        Instant startedAt,
        Instant endedAt,
        int scannedCount,
        int updatedCount,
        int failedCount,
        List<String> failedDocIds,
        String errorSummary
) {}
