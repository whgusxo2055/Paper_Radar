package com.paperradar.admin.service;

import com.paperradar.admin.model.ActiveConfig;
import java.util.List;

public interface KeywordAdminService {
    List<String> suggestCandidates(int lookbackDays, int size);

    ActiveConfig config();
}

