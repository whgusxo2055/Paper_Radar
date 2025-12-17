package com.paperradar.admin.service;

import com.paperradar.admin.model.InstitutionSummary;
import java.util.List;

public interface InstitutionService {
    InstitutionSummary upsertInstitution(String id, String displayName, String alias);

    InstitutionSummary setActive(String id, boolean active);

    List<InstitutionSummary> getByIds(List<String> ids);
}

