package com.paperradar.ingest.service;

import com.paperradar.ingest.model.IngestJob;
import com.paperradar.ingest.model.IngestMode;

public interface IngestService {
    IngestJob run(IngestMode mode);
}

