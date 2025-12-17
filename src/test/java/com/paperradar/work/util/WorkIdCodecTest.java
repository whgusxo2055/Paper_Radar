package com.paperradar.work.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WorkIdCodecTest {

    @Test
    void encodesAndDecodesRoundTrip() {
        String raw = "openalex:W1234567890";
        assertEquals(raw, WorkIdCodec.decode(WorkIdCodec.encode(raw)));
    }
}

