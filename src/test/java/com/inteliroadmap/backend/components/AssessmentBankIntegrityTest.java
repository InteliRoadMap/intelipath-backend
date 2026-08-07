package com.inteliroadmap.backend.components;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inteliroadmap.backend.domain.enums.AssessmentItemKind;
import com.inteliroadmap.backend.domain.model.AssessmentChoice;
import com.inteliroadmap.backend.domain.model.AssessmentItem;
import com.inteliroadmap.backend.domain.model.AssessmentPaper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads the real question banks and checks the things that would silently mis-grade
 * a student.
 *
 * <p>These are the failures worth automating because they are invisible by
 * inspection: an answer key naming an option that does not exist marks everyone
 * wrong, a duplicated item id makes one answer overwrite another, a rubric worth
 * zero points makes a code question count for nothing. None of them throws at
 * runtime; they just produce a wrong level and no error.
 */
class AssessmentBankIntegrityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AssessmentPaper load(String resource) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is not on the classpath");
            return MAPPER.readValue(in, AssessmentPaper.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"assessment/backend.json", "assessment/frontend.json",
            "assessment/fullstack.json"})
    void everyAnswerKeyNamesAnOptionThatExists(String resource) throws Exception {
        for (AssessmentItem item : load(resource).items()) {
            if (!item.kind().isAutoGraded()) continue;
            Set<String> keys = new HashSet<>(item.choices().stream()
                    .map(AssessmentChoice::key).toList());
            assertFalse(item.answer().isEmpty(), item.id() + " has no answer key");
            for (String correct : item.answer()) {
                assertTrue(keys.contains(correct),
                        item.id() + " keys '" + correct + "', which is not one of its options "
                                + keys + " — every student would be marked wrong");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"assessment/backend.json", "assessment/frontend.json",
            "assessment/fullstack.json"})
    void itemIdsAreUnique(String resource) throws Exception {
        List<AssessmentItem> items = load(resource).items();
        Set<String> ids = new HashSet<>(items.stream().map(AssessmentItem::id).toList());
        assertEquals(items.size(), ids.size(),
                resource + " has a duplicate item id — one answer would overwrite another");
    }

    @ParameterizedTest
    @ValueSource(strings = {"assessment/backend.json", "assessment/frontend.json",
            "assessment/fullstack.json"})
    void everyItemIsGradableSomehow(String resource) throws Exception {
        for (AssessmentItem item : load(resource).items()) {
            assertTrue(item.tier() >= 1 && item.tier() <= 3, item.id() + " has tier " + item.tier());
            assertNotNull(item.prompt(), item.id() + " has no prompt");
            assertNotNull(item.explanation(), item.id() + " has no explanation to show afterwards");
            if (item.kind().isAutoGraded()) {
                assertTrue(item.choices() != null && item.choices().size() >= 2,
                        item.id() + " is auto-graded with fewer than two options");
            } else {
                assertTrue(item.rubric() != null && !item.rubric().isEmpty(),
                        item.id() + " is rubric-graded and has no rubric");
                assertTrue(item.rubric().stream().mapToInt(c -> c.points()).sum() > 0,
                        item.id() + "'s rubric is worth zero points, so the item cannot score");
            }
        }
    }

    /**
     * A paper that is all tier 1 cannot tell a fresher from a senior, and one that is
     * all tier 3 tells you nothing about a beginner. Every band has to be probed.
     */
    @ParameterizedTest
    @ValueSource(strings = {"assessment/backend.json", "assessment/frontend.json",
            "assessment/fullstack.json"})
    void everyTierAndBothGradingHalvesArePresent(String resource) throws Exception {
        List<AssessmentItem> items = load(resource).items();
        for (int tier = 1; tier <= 3; tier++) {
            final int t = tier;
            assertTrue(items.stream().anyMatch(item -> item.tier() == t),
                    resource + " has no tier " + t + " item");
        }
        assertTrue(items.stream().anyMatch(item -> item.kind() == AssessmentItemKind.CODE),
                resource + " has no coding question");
        assertTrue(items.stream().anyMatch(item -> item.kind() == AssessmentItemKind.SHORT_ANSWER),
                resource + " has no written question");
        assertTrue(items.stream().filter(item -> item.kind().isAutoGraded()).count() >= 8,
                resource + " has too few auto-graded items to carry the objective score");
    }

    /** The loader matches a paper to a career by name, so the names have to be real. */
    @Test
    void thePapersCoverTheThreeCareersTheyClaim() throws Exception {
        assertEquals(List.of("Backend"), load("assessment/backend.json").careerNames());
        assertEquals(List.of("Frontend"), load("assessment/frontend.json").careerNames());
        assertTrue(load("assessment/fullstack.json").careerNames().contains("Full Stack"),
                "the career_roles row is named 'Full Stack'; a paper that does not list that "
                        + "spelling is never served");
    }
}
