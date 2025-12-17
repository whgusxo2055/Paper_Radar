package com.paperradar.institution.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class InstitutionIdCodec {

    private InstitutionIdCodec() {}

    public static String encode(String institutionId) {
        if (institutionId == null || institutionId.isBlank()) {
            return "";
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(institutionId.getBytes(StandardCharsets.UTF_8));
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

