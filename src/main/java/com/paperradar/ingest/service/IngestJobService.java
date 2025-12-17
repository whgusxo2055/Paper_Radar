package com.paperradar.ingest.service;

import com.paperradar.ingest.model.IngestJob;
import com.paperradar.ingest.model.IngestMode;
import com.paperradar.ingest.model.IngestStatus;
import java.util.List;
import java.util.Optional;

public interface IngestJobService {
    IngestJob start(IngestMode mode);

    void markFinished(
            String jobId,
            IngestStatus status,
            int processed,
            int created,
            int updated,
            String errorSummary
    );

    Optional<IngestJob> lastSuccessful(IngestMode mode);

    List<IngestJob> recentJobs(int size);
}

