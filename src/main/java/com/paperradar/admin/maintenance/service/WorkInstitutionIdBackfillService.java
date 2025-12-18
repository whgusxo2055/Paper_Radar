package com.paperradar.admin.maintenance.service;

import com.paperradar.admin.maintenance.model.WorkInstitutionIdBackfillResult;

public interface WorkInstitutionIdBackfillService {
    WorkInstitutionIdBackfillResult normalizeInstitutionIds(int batchSize, int maxDocs);
}

