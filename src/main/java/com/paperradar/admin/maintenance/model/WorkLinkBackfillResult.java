package com.paperradar.admin.maintenance.model;

import java.util.List;

public record WorkLinkBackfillResult(
        int scanned,
        int updated,
        int failed,
        List<String> failedDocIds
) {}
