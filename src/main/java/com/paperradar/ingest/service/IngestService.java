package com.paperradar.ingest.service;

import com.paperradar.ingest.model.IngestJob;
import com.paperradar.ingest.model.IngestMode;
import java.time.LocalDate;

public interface IngestService {
    IngestJob run(IngestMode mode);

    default IngestJob run(IngestMode mode, LocalDate fromPublicationDate, LocalDate toPublicationDate) {
        return run(mode);
    }
}
