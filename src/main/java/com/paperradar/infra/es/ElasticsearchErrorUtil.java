package com.paperradar.infra.es;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;

public final class ElasticsearchErrorUtil {

    private ElasticsearchErrorUtil() {}

    public static boolean isIndexNotFound(ElasticsearchException e) {
        if (e == null || e.error() == null) {
            return false;
        }
        return "index_not_found_exception".equals(e.error().type());
    }
}

