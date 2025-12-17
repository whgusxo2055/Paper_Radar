package com.paperradar.admin.maintenance.service;

import com.paperradar.admin.maintenance.model.WorkLinkBackfillResult;

public interface WorkLinkBackfillService {
    WorkLinkBackfillResult recomputeBestLinks(int batchSize, int maxDocs);
}

