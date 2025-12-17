package com.paperradar.ingest.openalex;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class AbstractInvertedIndexUtil {

    private AbstractInvertedIndexUtil() {}

    static String toText(JsonNode invertedIndex) {
        if (invertedIndex == null || invertedIndex.isMissingNode() || invertedIndex.isNull() || !invertedIndex.isObject()) {
            return "";
        }
        Map<Integer, String> posToToken = new HashMap<>();
        invertedIndex.fields().forEachRemaining(entry -> {
            String token = entry.getKey();
            JsonNode positions = entry.getValue();
            if (!positions.isArray()) {
                return;
            }
            for (JsonNode p : positions) {
                if (p.isInt()) {
                    posToToken.put(p.asInt(), token);
                }
            }
        });
        if (posToToken.isEmpty()) {
            return "";
        }
        List<Map.Entry<Integer, String>> sorted = new ArrayList<>(posToToken.entrySet());
        sorted.sort(Comparator.comparingInt(Map.Entry::getKey));
        StringBuilder sb = new StringBuilder(sorted.size() * 6);
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(sorted.get(i).getValue());
        }
        return sb.toString();
    }
}

