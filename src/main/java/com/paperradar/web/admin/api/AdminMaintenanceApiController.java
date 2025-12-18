package com.paperradar.web.admin.api;

import com.paperradar.admin.maintenance.model.WorkLinkBackfillResult;
import com.paperradar.admin.maintenance.model.WorkInstitutionIdBackfillResult;
import com.paperradar.admin.maintenance.model.MaintenanceJobType;
import com.paperradar.admin.maintenance.service.InstitutionIdBackfillRunRegistry;
import com.paperradar.admin.maintenance.service.MaintenanceJobService;
import com.paperradar.admin.maintenance.service.MaintenanceRunRegistry;
import com.paperradar.admin.maintenance.service.WorkLinkBackfillService;
import com.paperradar.admin.maintenance.service.WorkInstitutionIdBackfillService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminMaintenanceApiController {

    private final WorkLinkBackfillService backfillService;
    private final WorkInstitutionIdBackfillService institutionIdBackfillService;
    private final MaintenanceJobService maintenanceJobService;
    private final MaintenanceRunRegistry registry;
    private final InstitutionIdBackfillRunRegistry institutionIdRegistry;
    private final ThreadPoolTaskExecutor ingestTaskExecutor;

    @PostMapping("/api/admin/maintenance/recompute-work-links")
    public Map<String, String> recompute(@RequestBody BackfillRequest req) {
        if (!registry.tryStart()) {
            return Map.of("status", "busy");
        }

        var job = maintenanceJobService.start(MaintenanceJobType.recompute_work_links);
        CompletableFuture.runAsync(() -> {
            try {
                WorkLinkBackfillResult result = backfillService.recomputeBestLinks(req.batchSize(), req.maxDocs());
                maintenanceJobService.markSuccess(job.jobId(), result.scanned(), result.updated(), result.failed(), result.failedDocIds());
                registry.finish(result);
            } catch (Exception e) {
                maintenanceJobService.markFailed(
                        job.jobId(),
                        e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage()),
                        0,
                        java.util.List.of()
                );
                registry.abort();
            }
        }, ingestTaskExecutor);

        return Map.of("status", "started");
    }

    @PostMapping("/api/admin/maintenance/normalize-work-institution-ids")
    public Map<String, String> normalizeInstitutionIds(@RequestBody BackfillRequest req) {
        if (!institutionIdRegistry.tryStart()) {
            return Map.of("status", "busy");
        }

        var job = maintenanceJobService.start(MaintenanceJobType.normalize_work_institution_ids);
        CompletableFuture.runAsync(() -> {
            try {
                WorkInstitutionIdBackfillResult result = institutionIdBackfillService.normalizeInstitutionIds(req.batchSize(), req.maxDocs());
                maintenanceJobService.markSuccess(job.jobId(), result.scanned(), result.updatedDocs(), result.failed(), result.failedDocIds());
                institutionIdRegistry.finish(result);
            } catch (Exception e) {
                maintenanceJobService.markFailed(
                        job.jobId(),
                        e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage()),
                        0,
                        java.util.List.of()
                );
                institutionIdRegistry.abort();
            }
        }, ingestTaskExecutor);

        return Map.of("status", "started");
    }

    public record BackfillRequest(
            @Min(1) @Max(200) int batchSize,
            @Min(1) @Max(20000) int maxDocs
    ) {}
}
