package com.inteliroadmap.backend.services.impl;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Salary strings arrive as free text from the job boards. Reading them wrongly does not fail
 * loudly — it just files a posting under the wrong bracket, so the distribution chart looks
 * plausible while being wrong.
 */
class MarketTrendSalaryParsingTest {

    private static double parse(String text) {
        OptionalDouble parsed = MarketTrendServiceImpl.monthlySalaryInMillions(text);
        assertTrue(parsed.isPresent(), "expected a salary to be parsed from: " + text);
        return parsed.getAsDouble();
    }

    private static void assertUnquantified(String text) {
        assertTrue(MarketTrendServiceImpl.monthlySalaryInMillions(text).isEmpty(),
                "expected no salary to be parsed from: " + text);
    }

    @Test
    void readsAThousandsSeparatorAsOneNumber() {
        // Regression: "\d+" split this into 2, 0, 2 and 500, averaging 126 instead of 2250,
        // which then landed a ~56M role in the lowest bracket.
        assertEquals(56.25, parse("2,000 - 2,500 USD"), 0.001);
        assertEquals(12.5, parse("10.000.000 - 15.000.000 VND"), 0.001);
    }

    @Test
    void takesTheMidpointOfARange() {
        assertEquals(15, parse("10 - 20 triệu"), 0.001);
    }

    @Test
    void readsTheCurrencyFromTheTextRatherThanTheMagnitude() {
        assertEquals(25, parse("1000 USD"), 0.001);
        // A bare number is the millions-of-dong everyone writes by hand.
        assertEquals(120, parse("120"), 0.001);
    }

    @Test
    void letsVndWordingWinWhenTheScraperEmitsBothCurrencies() {
        // itviec_parser appends the currency, which can produce "<amount> vnd USD".
        assertEquals(22, parse("Up to 22 million vnd USD"), 0.001);
    }

    @Test
    void treatsWholeDongAsMillions() {
        assertEquals(15, parse("15000000"), 0.001);
    }

    @Test
    void readsADongUnitWrittenAgainstTheDigits() {
        // Live rows still carry the currency the scraper used to append, so the trailing
        // USD has to lose to the "đ"/"M" the employer actually wrote.
        assertEquals(40, parse("30,000,000 - 50,000,000đ USD"), 0.001);
        assertEquals(40, parse("30M - UP TO 50M USD"), 0.001);
        assertEquals(25, parse("20tr - 30tr"), 0.001);
    }

    @Test
    void doesNotMistakeAWordStartingWithMForAUnit() {
        // "1500 USD/month" is dollars; the boundary must not let "month" read as millions.
        assertEquals(37.5, parse("1500 USD/month"), 0.001);
    }

    @Test
    void ignoresPostingsThatQuoteNoFigure() {
        assertUnquantified("You'll love it");
        assertUnquantified("Very Attractive!!!");
        assertUnquantified("Thỏa thuận theo năng lực");
        assertUnquantified("Thương lượng");
        assertUnquantified("Negotiable");
        assertUnquantified(null);
    }
}
