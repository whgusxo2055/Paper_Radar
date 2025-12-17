package com.paperradar.admin.service;

import com.paperradar.admin.model.ActiveConfig;

public interface ConfigService {
    ActiveConfig getActiveConfig();

    ActiveConfig enableKeyword(String keyword);

    ActiveConfig disableKeyword(String keyword);

    ActiveConfig enableInstitution(String instId);

    ActiveConfig disableInstitution(String instId);
}

