package com.paperradar.search.service;

import com.paperradar.search.dto.SearchRequest;
import com.paperradar.search.model.SearchResultPage;

public interface SearchService {
    SearchResultPage search(SearchRequest request);
}

