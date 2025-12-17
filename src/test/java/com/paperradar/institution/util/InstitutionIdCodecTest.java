package com.paperradar.institution.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class InstitutionIdCodecTest {

    @Test
    void encodesAndDecodesRoundTrip() {
        String raw = "https://openalex.org/I123456789";
        String encoded = InstitutionIdCodec.encode(raw);
        assertEquals(raw, InstitutionIdCodec.decode(encoded));
    }

    @Test
    void invalidDecodeReturnsEmpty() {
        assertEquals("", InstitutionIdCodec.decode("%%%"));
    }
}

