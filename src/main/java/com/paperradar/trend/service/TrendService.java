package com.paperradar.trend.service;

import com.paperradar.trend.model.TrendItem;
import java.util.List;

public interface TrendService {
    List<TrendItem> keywordTrends(int topN);

    List<TrendItem> institutionTrends(int topN);
}

