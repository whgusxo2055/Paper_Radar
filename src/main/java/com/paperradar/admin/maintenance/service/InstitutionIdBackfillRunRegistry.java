package com.paperradar.admin.maintenance.service;

import com.paperradar.admin.maintenance.model.WorkInstitutionIdBackfillResult;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class InstitutionIdBackfillRunRegistry {

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<WorkInstitutionIdBackfillResult> lastResult = new AtomicReference<>(null);
    private final AtomicReference<Instant> lastRunAt = new AtomicReference<>(null);

    public boolean tryStart() {
        return running.compareAndSet(false, true);
    }

    public void finish(WorkInstitutionIdBackfillResult result) {
        lastResult.set(result);
        lastRunAt.set(Instant.now());
        running.set(false);
    }

    public void abort() {
        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }

    public WorkInstitutionIdBackfillResult lastResult() {
        return lastResult.get();
    }

    public Instant lastRunAt() {
        return lastRunAt.get();
    }
}

