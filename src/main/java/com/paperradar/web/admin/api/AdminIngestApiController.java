package com.paperradar.web.admin.api;

import com.paperradar.ingest.model.IngestJob;
import com.paperradar.ingest.model.IngestMode;
import com.paperradar.ingest.service.IngestService;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminIngestApiController {

    private final IngestService ingestService;
    private final ThreadPoolTaskExecutor ingestTaskExecutor;

    @PostMapping("/api/admin/ingest/run")
    public Map<String, String> run(@RequestBody IngestRunRequest req) {
        CompletableFuture.runAsync(() -> ingestService.run(req.mode()), ingestTaskExecutor);
        return Map.of("status", "started", "mode", req.mode().name());
    }

    public record IngestRunRequest(@NotNull IngestMode mode) {}
}

