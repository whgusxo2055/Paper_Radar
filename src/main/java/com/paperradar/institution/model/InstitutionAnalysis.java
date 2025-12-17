package com.paperradar.institution.model;

import com.paperradar.search.model.WorkSummary;
import java.util.List;

public record InstitutionAnalysis(
        String institutionId,
        String displayName,
        long works30d,
        long works90d,
        List<String> topKeywords,
        List<WorkSummary> recentWorks
) {}

