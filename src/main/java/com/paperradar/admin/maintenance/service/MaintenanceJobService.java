package com.paperradar.admin.maintenance.service;

import com.paperradar.admin.maintenance.model.MaintenanceJob;
import com.paperradar.admin.maintenance.model.MaintenanceJobType;
import java.util.List;
import java.util.Optional;

public interface MaintenanceJobService {
    MaintenanceJob start(MaintenanceJobType type);

    void markSuccess(String jobId, int scanned, int updated, int failedCount, java.util.List<String> failedDocIds);

    void markFailed(String jobId, String errorSummary, int failedCount, java.util.List<String> failedDocIds);

    List<MaintenanceJob> recentJobs(int size);

    Optional<MaintenanceJob> latestOfType(MaintenanceJobType type);
}
