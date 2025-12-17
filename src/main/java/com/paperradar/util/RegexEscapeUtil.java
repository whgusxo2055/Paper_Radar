package com.paperradar.util;

public final class RegexEscapeUtil {

    private RegexEscapeUtil() {}

    public static String escapeForElasticsearchRegex(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length() * 2);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (isRegexMeta(c)) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static boolean isRegexMeta(char c) {
        return switch (c) {
            case '.', '^', '$', '*', '+', '?', '(', ')', '[', ']', '{', '}', '|', '\\' -> true;
            default -> false;
        };
    }
}

