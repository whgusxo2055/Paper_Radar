package com.paperradar.institution.service;

import com.paperradar.institution.model.InstitutionAnalysis;

public interface InstitutionAnalysisService {
    InstitutionAnalysis analyze(String institutionId, int recentSize, int topKeywordsSize, int keywordWindowDays);
}
