package com.paperradar.ingest.service.enrich;

import com.paperradar.ingest.model.OpenAlexWork;

public interface WorkLinkEnricher {
    OpenAlexWork enrich(OpenAlexWork work);
}

