package com.paperradar.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class KeywordNormalizeUtilTest {

    @Test
    void normalizesByLowercasingAndCollapsingWhitespace() {
        assertEquals("graph neural network", KeywordNormalizeUtil.normalize("  Graph   Neural\tNetwork "));
        assertEquals("llm", KeywordNormalizeUtil.normalize("LLM"));
    }

    @Test
    void nullOrBlankBecomesEmpty() {
        assertEquals("", KeywordNormalizeUtil.normalize(null));
        assertEquals("", KeywordNormalizeUtil.normalize(""));
        assertEquals("", KeywordNormalizeUtil.normalize("   "));
    }
}

