package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.dto.response.market.SkillDemandResponse;
import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the TF-IDF weighting that replaced the flat 8%-of-postings threshold.
 *
 * <p>Measured on the 866 postings in the live database, the old rule produced one
 * list for all eight careers — AI 27%, Agile 24%, Python 21% — because it ranked
 * on how common a skill is, and common skills are the ones that say least about
 * which role a student is heading for.
 */
class MarketDemandMapperTest {

    private final MarketDemandMapper mapper = new MarketDemandMapper();

    private static final int CAREERS = 8;
    private static final int SAMPLE = 866;

    /**
     * The whole point. Both skills are asked for by the same share of the market;
     * the one only this career names has to win.
     */
    @Test
    void aSkillOnlyThisCareerNamesOutranksAnEquallyCommonGenericOne() {
        double specific = mapper.relevanceOf(0.17, ImportanceLevel.HIGH, 1, CAREERS);
        double generic = mapper.relevanceOf(0.17, ImportanceLevel.HIGH, 6, CAREERS);

        assertTrue(specific > generic,
                "same frequency, but a one-career skill discriminates and a six-career one does not");
    }

    /**
     * A skill every career names tells us nothing about which career you are in, so it
     * scores zero — and never less.
     *
     * <p>This test used to assert the opposite ({@code < 0}, "an utterly generic skill
     * cannot lead a ranking"). The negative was not free: {@code RoadmapEdgeResolver}
     * turns relevance into a market pull by dividing by the career's maximum, so a
     * negative score is a roadmap actively steering a student <em>away</em> from Java
     * for the crime of being wanted everywhere. Zero is the honest floor — no
     * discriminating power, not anti-discriminating power.
     */
    @Test
    void aSkillEveryCareerNamesScoresZeroAndNeverLess() {
        assertEquals(0.0, mapper.relevanceOf(0.90, ImportanceLevel.HIGH, CAREERS, CAREERS), 0.0,
                "ln(8/9) is negative; the clamp must absorb it rather than pass it on");
    }

    /** Clamping the floor must not flatten anything above it. */
    @Test
    void theClampLeavesOrderingAboveZeroIntact() {
        double specific = mapper.relevanceOf(0.17, ImportanceLevel.HIGH, 1, CAREERS);
        double middling = mapper.relevanceOf(0.17, ImportanceLevel.HIGH, 4, CAREERS);

        assertTrue(specific > middling && middling > 0);
    }

    /** A rare skill still has to be asked for by someone; frequency remains a factor. */
    @Test
    void raritySlonelDoesNotWin() {
        double rareAndUnwanted = mapper.relevanceOf(0.001, ImportanceLevel.HIGH, 1, CAREERS);
        double commonAndFairlySpecific = mapper.relevanceOf(0.19, ImportanceLevel.HIGH, 2, CAREERS);

        assertTrue(commonAndFairlySpecific > rareAndUnwanted);
    }

    /** The career's own grading is the third factor, between two equally distinctive skills. */
    @Test
    void theCareersOwnGradingSeparatesEquallyDistinctiveSkills() {
        double high = mapper.relevanceOf(0.10, ImportanceLevel.HIGH, 2, CAREERS);
        double avg = mapper.relevanceOf(0.10, ImportanceLevel.AVG, 2, CAREERS);
        double low = mapper.relevanceOf(0.10, ImportanceLevel.LOW, 2, CAREERS);

        assertTrue(high > avg && avg > low);
    }

    /**
     * Real numbers from the live data, so a refactor that silently changes the
     * formula fails here rather than in a student's roadmap. SQL is named by 2 of
     * 8 careers and appears in 19% of postings.
     */
    @Test
    void sqlForBackendScoresWhatTheQueryPlanSaidItWould() {
        double relevance = mapper.relevanceOf(0.1905, ImportanceLevel.HIGH, 2, CAREERS);

        assertEquals(0.1868, relevance, 0.001);
    }

    @Test
    void aSkillBelowTheDemandFloorIsNotReported() {
        assertNull(mapper.toSkillDemandResponse(4, SAMPLE, 90, ImportanceLevel.LOW, 5, CAREERS),
                "marginal, low-graded and unremarkable — nothing worth saying");
    }

    /**
     * The defect this gate was moved to fix. Python was named by 193 of 913 postings —
     * the second-most-asked skill for Backend — and by seven of the eight careers, which
     * put its relevance at exactly 0.0000 and deleted it from every screen. A student
     * holding Python at PROFESSIONAL could not find it on a chart of their own skills.
     */
    @Test
    void aSkillWantedByEveryCareerIsStillReported() {
        SkillDemandResponse demand = mapper.toSkillDemandResponse(193, 913, 90,
                ImportanceLevel.HIGH, 7, CAREERS);

        assertNotNull(demand, "zero discriminating power is not a reason to say nothing");
        assertEquals(0.211, demand.getFrequency(), 0.001);
        assertEquals(0.0, demand.getRelevance(), 0.0001);
    }

    /**
     * A career's own grading raises the bar rather than lowering it: the same market
     * demand clears the floor at HIGH and fails it at LOW. 8 of 866 is 0.92% — above the
     * floor at full weight, a third of that at LOW.
     */
    @Test
    void theCareersGradingDecidesHowMuchMarketEvidenceTheFloorNeeds() {
        assertNotNull(mapper.toSkillDemandResponse(8, SAMPLE, 90, ImportanceLevel.HIGH, 2, CAREERS));
        assertNull(mapper.toSkillDemandResponse(8, SAMPLE, 90, ImportanceLevel.LOW, 2, CAREERS));
    }

    @Test
    void tooFewPostingsMeansNoFigureAtAll() {
        assertNull(mapper.toSkillDemandResponse(10, MarketDemandMapper.MIN_SAMPLE - 1, 90,
                ImportanceLevel.HIGH, 1, CAREERS));
    }

    @Test
    void aSkillOutCountingTheWindowIsClampedRatherThanRenderedPastFull() {
        SkillDemandResponse demand = mapper.toSkillDemandResponse(
                SAMPLE * 2, SAMPLE, 90, ImportanceLevel.HIGH, 1, CAREERS);

        assertNotNull(demand);
        assertEquals(1.0, demand.getFrequency(), 0.0001);
    }

    /**
     * The reason string has to carry the half of the ranking a percentage cannot
     * explain, or a student sees a skill ranked first with a lower percentage than
     * one ranked below it and concludes the numbers are broken.
     */
    @Test
    void theReasonExplainsDistinctivenessNotJustFrequency() {
        SkillDemandResponse only = mapper.toSkillDemandResponse(150, SAMPLE, 90,
                ImportanceLevel.HIGH, 1, CAREERS);
        SkillDemandResponse shared = mapper.toSkillDemandResponse(150, SAMPLE, 90,
                ImportanceLevel.HIGH, 3, CAREERS);

        assertNotNull(only);
        assertNotNull(shared);
        assertTrue(only.getReason().contains("no other career"), only.getReason());
        assertTrue(shared.getReason().contains("3 of 8 careers"), shared.getReason());
    }

    /** The counts behind the ratio stay on the wire; a percentage alone hides its sample. */
    @Test
    void theRawCountsSurviveOntoTheResponse() {
        SkillDemandResponse demand = mapper.toSkillDemandResponse(150, SAMPLE, 90,
                ImportanceLevel.HIGH, 2, CAREERS);

        assertNotNull(demand);
        assertEquals(150, demand.getJobCount());
        assertEquals(SAMPLE, demand.getSampleSize());
        assertEquals(2, demand.getCareersNaming());
        assertEquals(90, demand.getWindowDays());
    }
}
