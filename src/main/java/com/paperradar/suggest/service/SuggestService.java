package com.paperradar.suggest.service;

import com.paperradar.suggest.model.SuggestItem;
import java.util.List;

public interface SuggestService {
    List<SuggestItem> suggestKeywords(String prefix, int size);

    List<SuggestItem> suggestInstitutions(String prefix, int size);

    List<SuggestItem> suggestAuthors(String prefix, int size);
}

