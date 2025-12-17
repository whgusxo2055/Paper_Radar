package com.paperradar.ingest.openalex;

import com.paperradar.ingest.model.OpenAlexWork;
import java.time.LocalDate;
import java.util.List;

public interface OpenAlexClient {
    List<OpenAlexWork> fetchWorksByKeyword(String keyword, LocalDate fromPublicationDate, LocalDate fromUpdatedDate);

    List<OpenAlexWork> fetchWorksByInstitution(String openAlexInstitutionId, LocalDate fromPublicationDate, LocalDate fromUpdatedDate);
}

