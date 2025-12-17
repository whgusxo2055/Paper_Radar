package com.paperradar.ingest.service.enrich;

import com.paperradar.ingest.model.OpenAlexWork;
import org.springframework.stereotype.Component;

@Component
public class NoopWorkLinkEnricher implements WorkLinkEnricher {
    @Override
    public OpenAlexWork enrich(OpenAlexWork work) {
        return work;
    }
}
