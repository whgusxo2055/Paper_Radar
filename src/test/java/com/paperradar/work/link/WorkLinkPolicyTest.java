package com.paperradar.work.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class WorkLinkPolicyTest {

    @Test
    void prefersDoiThenLandingThenPdf() {
        assertEquals(
                WorkLinkType.DOI,
                WorkLinkPolicy.pickBestLink("10.1000/xyz", "https://example.com", "https://x.com/a.pdf", null).type()
        );
        assertEquals(
                WorkLinkType.Landing,
                WorkLinkPolicy.pickBestLink("", "https://example.com", "https://x.com/a.pdf", null).type()
        );
        assertEquals(
                WorkLinkType.PDF,
                WorkLinkPolicy.pickBestLink("", "", "https://x.com/a.pdf", "https://oa.com").type()
        );
    }

    @Test
    void returnsNullWhenNothingAvailable() {
        assertNull(WorkLinkPolicy.pickBestLink(null, null, null, null));
    }
}

