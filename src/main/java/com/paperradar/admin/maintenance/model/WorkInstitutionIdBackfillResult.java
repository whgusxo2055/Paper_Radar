package com.paperradar.admin.maintenance.model;

import java.util.List;

public record WorkInstitutionIdBackfillResult(
        int scanned,
        int updatedDocs,
        int updatedInstitutionIds,
        int failed,
        List<String> failedDocIds
) {}

