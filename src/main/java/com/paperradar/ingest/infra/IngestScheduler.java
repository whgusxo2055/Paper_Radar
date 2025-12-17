package com.paperradar.ingest.infra;

import com.paperradar.ingest.model.IngestMode;
import com.paperradar.ingest.model.IngestStatus;
import com.paperradar.ingest.service.IngestService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IngestScheduler {

    private static final Logger log = LoggerFactory.getLogger(IngestScheduler.class);

    private final IngestService ingestService;

    @Scheduled(cron = "${INGEST_SCHEDULE_CRON:0 0 3 * * *}", zone = "${APP_TIMEZONE:Asia/Seoul}")
    public void runDailyIncremental() {
        int maxAttempts = 3;
        long backoffMs = 1_000L;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                var job = ingestService.run(IngestMode.incremental);
                if (job.status() == IngestStatus.success) {
                    return;
                }
            } catch (Exception e) {
                log.warn("Scheduled ingest attempt {} failed.", attempt, e);
            }

            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            backoffMs *= 3;
        }
    }
}

