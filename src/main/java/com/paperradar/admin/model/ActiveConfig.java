package com.paperradar.admin.model;

import java.time.Instant;
import java.util.List;

public record ActiveConfig(
        List<String> enabledKeywords,
        List<String> disabledKeywords,
        List<String> enabledInstitutions,
        List<String> disabledInstitutions,
        Instant updatedAt
) {}

