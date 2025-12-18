package com.paperradar.admin.service;

import com.paperradar.admin.model.ActiveConfig;

public interface ConfigService {
    ActiveConfig getActiveConfig();

    ActiveConfig enableKeyword(String keyword);

    ActiveConfig enableKeywords(java.util.List<String> keywords);

    ActiveConfig disableKeyword(String keyword);

    ActiveConfig disableKeywords(java.util.List<String> keywords);

    ActiveConfig enableInstitution(String instId);

    ActiveConfig disableInstitution(String instId);
}
