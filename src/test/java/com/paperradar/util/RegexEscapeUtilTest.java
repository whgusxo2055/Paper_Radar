package com.paperradar.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RegexEscapeUtilTest {

    @Test
    void escapesRegexMetaCharacters() {
        assertEquals("a\\+b", RegexEscapeUtil.escapeForElasticsearchRegex("a+b"));
        assertEquals("\\[abc\\]", RegexEscapeUtil.escapeForElasticsearchRegex("[abc]"));
        assertEquals("foo\\(bar\\)", RegexEscapeUtil.escapeForElasticsearchRegex("foo(bar)"));
        assertEquals("a\\\\b", RegexEscapeUtil.escapeForElasticsearchRegex("a\\b"));
    }

    @Test
    void blankOrNullBecomesEmptyString() {
        assertEquals("", RegexEscapeUtil.escapeForElasticsearchRegex(null));
        assertEquals("", RegexEscapeUtil.escapeForElasticsearchRegex(""));
        assertEquals("", RegexEscapeUtil.escapeForElasticsearchRegex("   "));
    }
}

