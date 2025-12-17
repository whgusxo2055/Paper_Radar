package com.paperradar.util;

import java.util.Locale;

public final class KeywordNormalizeUtil {

    private KeywordNormalizeUtil() {}

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String collapsedWhitespace = trimmed.replaceAll("\\s+", " ");
        return collapsedWhitespace.toLowerCase(Locale.ROOT);
    }
}

