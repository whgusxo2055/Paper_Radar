package com.paperradar.work.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class WorkIdCodec {

    private WorkIdCodec() {}

    public static String encode(String workId) {
        if (workId == null || workId.isBlank()) {
            return "";
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(workId.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return "";
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}

