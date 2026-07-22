package com.inteliroadmap.backend.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailUtilTest {

    @Test
    void rendersTheBoldHeadingsAuthorsActuallyType() {
        // The exact shape a mentor sent, which arrived as literal asterisks in one run-on line.
        String raw = "**Strengths:** Solid work\n**Areas for Improvement:** Testing\n**Recommendations:** Read up";

        String html = EmailUtil.renderFeedbackBody(raw);

        assertTrue(html.contains("<strong>Strengths:</strong> Solid work"));
        assertTrue(html.contains("<strong>Areas for Improvement:</strong> Testing"));
        assertFalse(html.contains("**"));
        // Single newlines stay inside one paragraph, as line breaks.
        assertTrue(html.contains("<br />"));
    }

    @Test
    void blankLinesBecomeSeparateParagraphs() {
        String html = EmailUtil.renderFeedbackBody("First point.\n\nSecond point.");

        assertEquals("<p>First point.</p><p>Second point.</p>", html);
    }

    @Test
    void feedbackCannotSmuggleMarkupIntoTheEmail() {
        String html = EmailUtil.renderFeedbackBody("<script>alert(1)</script> **bold**");

        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
        // Escaping must not disarm the formatting the author did intend.
        assertTrue(html.contains("<strong>bold</strong>"));
    }

    @Test
    void emptyFeedbackStillProducesValidMarkup() {
        assertEquals("<p></p>", EmailUtil.renderFeedbackBody(null));
        assertEquals("<p></p>", EmailUtil.renderFeedbackBody("   "));
    }

    @Test
    void escapesAmpersandsBeforeTags() {
        assertEquals("Tom &amp; Jerry &lt;b&gt;", EmailUtil.escapeHtml("Tom & Jerry <b>"));
    }
}
