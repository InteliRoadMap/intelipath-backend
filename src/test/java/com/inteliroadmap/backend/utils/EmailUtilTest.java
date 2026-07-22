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

        assertTrue(html.contains(">Strengths:</strong> Solid work"));
        assertTrue(html.contains(">Areas for Improvement:</strong> Testing"));
        assertFalse(html.contains("**"));
        // Single newlines stay inside one paragraph, as line breaks.
        assertTrue(html.contains("<br />"));
    }

    @Test
    void blankLinesBecomeSeparateParagraphs() {
        String html = EmailUtil.renderFeedbackBody("First point.\n\nSecond point.");

        // Spacing is carried inline, and the last paragraph drops its margin so the quote
        // block does not end with a gap the surrounding padding already provides.
        assertEquals("<p style=\"margin:0 0 12px;\">First point.</p>"
                + "<p style=\"margin:0;\">Second point.</p>", html);
    }

    @Test
    void feedbackCannotSmuggleMarkupIntoTheEmail() {
        String html = EmailUtil.renderFeedbackBody("<script>alert(1)</script> **bold**");

        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
        // Escaping must not disarm the formatting the author did intend.
        assertTrue(html.contains(">bold</strong>"));
    }

    @Test
    void emptyFeedbackStillProducesValidMarkup() {
        assertEquals("", EmailUtil.renderFeedbackBody(null));
        assertEquals("", EmailUtil.renderFeedbackBody("   "));
    }

    @Test
    void escapesAmpersandsBeforeTags() {
        assertEquals("Tom &amp; Jerry &lt;b&gt;", EmailUtil.escapeHtml("Tom & Jerry <b>"));
    }

    /**
     * The accent is a literal in each template because a text block cannot interpolate a
     * constant, so nothing but this stops the two drifting apart again — they were already
     * shipping in teal and a cyan-to-blue gradient at the same time.
     */
    @Test
    void everyEmailUsesTheOneBrandAccent() {
        for (String template : new String[]{
                EmailUtil.FEEDBACK_NOTIFICATION_EMAIL, EmailUtil.RESET_PASSWORD_LINK}) {
            assertTrue(template.contains(EmailUtil.BRAND_ACCENT),
                    "template does not use the brand accent " + EmailUtil.BRAND_ACCENT);
            assertFalse(template.contains("#00838f"), "template still uses the old teal");
            assertFalse(template.contains("linear-gradient"), "template still has a gradient banner");
        }
    }
}
