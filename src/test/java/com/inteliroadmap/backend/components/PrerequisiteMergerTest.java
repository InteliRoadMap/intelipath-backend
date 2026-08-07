package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.components.PrerequisiteMerger.Claim;
import com.inteliroadmap.backend.components.PrerequisiteMerger.MergeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrerequisiteMergerTest {

    private PrerequisiteMerger merger;

    private UUID java;
    private UUID spring;
    private UUID docker;

    @BeforeEach
    void setUp() {
        merger = new PrerequisiteMerger();
        java = UUID.randomUUID();
        spring = UUID.randomUUID();
        docker = UUID.randomUUID();
    }

    /** The author decided; the model may not overrule them. */
    @Test
    void anAuthorsOrderingBeatsAContradictingGeneratedOne() {
        MergeResult result = merger.merge(
                List.of(authored(java, spring)),
                List.of(generated(spring, java, "0.99")));

        assertEquals(1, result.accepted().size());
        assertTrue(result.accepted().get(0).fromAuthor());
        assertEquals(1, result.rejected().size());
        assertTrue(result.rejected().values().iterator().next().contains("opposite order"));
    }

    /**
     * A student never sees an ordering claim, only its consequences, so an
     * uncertain one is worse than none. It is dropped, not parked for review —
     * nothing on a student's path may wait on an approval queue.
     */
    @Test
    void anUncertainGeneratedClaimIsDiscardedOutright() {
        MergeResult result = merger.merge(List.of(), List.of(generated(java, spring, "0.55")));

        assertTrue(result.accepted().isEmpty());
        assertTrue(result.rejected().values().iterator().next().contains("below"));
    }

    /** "A before B before A" would lock both nodes for good. */
    @Test
    void aGeneratedClaimThatClosesACycleIsRejected() {
        MergeResult result = merger.merge(
                List.of(authored(java, spring), authored(spring, docker)),
                List.of(generated(docker, java, "0.95")));

        assertEquals(2, result.accepted().size());
        assertTrue(result.rejected().values().iterator().next().contains("cycle"));
    }

    /** An ordering nobody can question is not one worth recording. */
    @Test
    void aGeneratedClaimWithoutAReasonIsRejected() {
        Claim noReason = new Claim(java, spring, PrerequisiteMerger.SOURCE_AI, new BigDecimal("0.95"), "  ");

        MergeResult result = merger.merge(List.of(), List.of(noReason));

        assertTrue(result.accepted().isEmpty());
    }

    /** The whole point: generated claims that clear the bar do get through. */
    @Test
    void aConfidentNonConflictingGeneratedClaimIsAccepted() {
        MergeResult result = merger.merge(
                List.of(authored(java, spring)),
                List.of(generated(docker, spring, "0.90")));

        assertEquals(2, result.accepted().size());
        assertEquals(1, result.acceptedFromAuthor());
        assertTrue(result.rejected().isEmpty());
    }

    @Test
    void aNodeIsNeverItsOwnPrerequisite() {
        MergeResult result = merger.merge(
                List.of(authored(java, java)),
                List.of(generated(spring, spring, "0.99")));

        assertTrue(result.accepted().isEmpty());
        assertEquals(2, result.rejected().size());
    }

    /** Chains stay valid: A→B and B→C must both survive, they are not a cycle. */
    @Test
    void aLongerChainIsNotMistakenForACycle() {
        MergeResult result = merger.merge(
                List.of(),
                List.of(generated(java, spring, "0.90"), generated(spring, docker, "0.90")));

        assertEquals(2, result.accepted().size());
        assertFalse(result.rejected().values().stream().anyMatch(r -> r.contains("cycle")));
    }

    private Claim authored(UUID before, UUID after) {
        return new Claim(before, after, PrerequisiteMerger.SOURCE_AUTHOR, BigDecimal.ONE,
                "Ordered by the source roadmap.");
    }

    private Claim generated(UUID before, UUID after, String confidence) {
        return new Claim(before, after, PrerequisiteMerger.SOURCE_AI, new BigDecimal(confidence),
                "Generated ordering.");
    }
}
