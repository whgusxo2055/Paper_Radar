package com.paperradar.trend.model;

public record TrendItem(
        String key,
        String label,
        double trendScore,
        double ma7,
        double ma30,
        double total30
) {}

