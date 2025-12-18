package com.paperradar.ingest.openalex;

import java.util.List;

public interface OpenAlexInstitutionClient {

    List<OpenAlexInstitutionSummary> searchInstitutions(String query, int size);

    OpenAlexInstitutionSummary getInstitution(String institutionId);

    record OpenAlexInstitutionSummary(String id, String displayName) {}
}

