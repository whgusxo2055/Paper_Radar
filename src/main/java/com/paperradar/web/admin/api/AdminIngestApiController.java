package com.paperradar.web.admin.api;

import com.paperradar.ingest.model.IngestJob;
import com.paperradar.ingest.model.IngestMode;
import com.paperradar.ingest.service.IngestService;
import com.paperradar.ingest.service.IngestJobService;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminIngestApiController {

    private final IngestService ingestService;
    private final ThreadPoolTaskExecutor ingestTaskExecutor;
    private final IngestJobService ingestJobService;

    @Value("${INGEST_STALE_JOB_THRESHOLD_MINUTES:30}")
    private int staleThresholdMinutes;

    @PostMapping("/api/admin/ingest/run")
    public Map<String, String> run(@RequestBody IngestRunRequest req) {
        if (req == null || req.mode() == null) {
            return Map.of("status", "error", "message", "mode는 필수입니다.");
        }

        if (req.mode() == IngestMode.full) {
            LocalDate from = req.from();
            LocalDate to = req.to();
            if ((from == null) != (to == null)) {
                return Map.of("status", "error", "message", "전체 수집(full)은 from/to를 함께 입력하세요.");
            }
            if (from != null && to != null && from.isAfter(to)) {
                return Map.of("status", "error", "message", "from은 to보다 클 수 없습니다.");
            }
            CompletableFuture.runAsync(() -> ingestService.run(req.mode(), from, to), ingestTaskExecutor);
            return Map.of("status", "started", "mode", req.mode().name());
        }

        CompletableFuture.runAsync(() -> ingestService.run(req.mode()), ingestTaskExecutor);
        return Map.of("status", "started", "mode", req.mode().name());
    }

    @PostMapping("/api/admin/ingest/cleanup")
    public Map<String, Object> cleanup(@RequestBody CleanupRequest req) {
        int defaultMinutes = Math.max(1, staleThresholdMinutes);
        int minutes = req == null || req.olderThanMinutes() == null ? defaultMinutes : Math.max(1, req.olderThanMinutes());
        int cleaned = ingestJobService.cleanupStaleRunningJobs(Duration.ofMinutes(minutes));
        return Map.of("status", "ok", "cleaned", cleaned, "olderThanMinutes", minutes);
    }

    public record IngestRunRequest(@NotNull IngestMode mode, LocalDate from, LocalDate to) {}

    public record CleanupRequest(Integer olderThanMinutes) {}
}
