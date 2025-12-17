package com.paperradar.ingest.openalex;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AbstractInvertedIndexUtilTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void reconstructsAbstractFromInvertedIndex() throws Exception {
        String json = """
                {
                  "hello": [0],
                  "world": [1],
                  "again": [2]
                }
                """;
        assertEquals("hello world again", AbstractInvertedIndexUtil.toText(om.readTree(json)));
    }

    @Test
    void missingOrNonObjectReturnsEmpty() throws Exception {
        assertEquals("", AbstractInvertedIndexUtil.toText(om.readTree("null")));
        assertEquals("", AbstractInvertedIndexUtil.toText(om.readTree("[]")));
    }
}

