package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapRecommendationDecisionResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapRecommendationResponse;
import com.inteliroadmap.backend.services.RoadmapPersonalizationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The ids this returns become the animation on the canvas, so what it counts has
 * to be what the student will see ticked — not what was offered, and not what was
 * accepted.
 */
class RoadmapRefreshTriggerTest {

    private final RoadmapPersonalizationService personalization = mock(RoadmapPersonalizationService.class);
    private final RoadmapRefreshTrigger trigger = new RoadmapRefreshTrigger(personalization);

    private static RoadmapRecommendationResponse recommendation(UUID id) {
        RoadmapRecommendationResponse response = new RoadmapRecommendationResponse();
        response.setRecommendationId(id);
        return response;
    }

    private static RoadmapRecommendationDecisionResponse decision(UUID... nodeIds) {
        return RoadmapRecommendationDecisionResponse.builder()
                .completedNodeCount(nodeIds.length)
                .completedNodeIds(List.of(nodeIds))
                .build();
    }

    @Test
    void collectsTheNodesEveryAcceptedRecommendationMarked() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        UUID recA = UUID.randomUUID();
        UUID recB = UUID.randomUUID();

        when(personalization.generateRecommendationsForCurrentStudent())
                .thenReturn(List.of(recommendation(recA), recommendation(recB)));
        when(personalization.acceptRecommendation(recA)).thenReturn(decision(first, second));
        when(personalization.acceptRecommendation(recB)).thenReturn(decision(third));

        assertEquals(List.of(first, second, third), trigger.refreshAndCollect("test"));
    }

    /**
     * An accepted recommendation whose every item was gated out marks nothing. It
     * must contribute no ids — promising a tick the canvas cannot draw is worse
     * than promising none, because the student then looks for it.
     */
    @Test
    void aRecommendationThatMarkedNothingContributesNothing() {
        UUID rec = UUID.randomUUID();
        when(personalization.generateRecommendationsForCurrentStudent())
                .thenReturn(List.of(recommendation(rec)));
        when(personalization.acceptRecommendation(rec)).thenReturn(decision());

        assertTrue(trigger.refreshAndCollect("test").isEmpty());
        assertEquals(0, trigger.refreshCurrentStudent("test"));
    }

    /** One bad recommendation must not strand the others, nor their ids. */
    @Test
    void oneFailureDoesNotLoseTheRest() {
        UUID good = UUID.randomUUID();
        UUID bad = UUID.randomUUID();
        UUID node = UUID.randomUUID();

        when(personalization.generateRecommendationsForCurrentStudent())
                .thenReturn(List.of(recommendation(bad), recommendation(good)));
        when(personalization.acceptRecommendation(bad)).thenThrow(new IllegalStateException("gated"));
        when(personalization.acceptRecommendation(good)).thenReturn(decision(node));

        assertEquals(List.of(node), trigger.refreshAndCollect("test"));
    }

    /**
     * Personalization failing is not the caller's failure. The evidence is already
     * persisted; the refresh is a consequence, so it returns empty rather than
     * throwing into an import or a graded submission that already succeeded.
     */
    @Test
    void aBrokenEngineIsSwallowed() {
        when(personalization.generateRecommendationsForCurrentStudent())
                .thenThrow(new IllegalStateException("down"));

        assertTrue(trigger.refreshAndCollect("test").isEmpty());
    }

    @Test
    void nothingToApplyIsNotAnError() {
        when(personalization.generateRecommendationsForCurrentStudent()).thenReturn(List.of());

        assertTrue(trigger.refreshAndCollect("test").isEmpty());
        // The old count-only contract has to keep meaning the same thing.
        assertEquals(0, trigger.refreshCurrentStudent("test"));
    }

    /**
     * A null decision, or one from an older code path that never filled the ids,
     * must not take the whole wave down with an NPE.
     */
    @Test
    void aDecisionWithoutIdsIsSkippedNotFatal() {
        UUID withIds = UUID.randomUUID();
        UUID withoutIds = UUID.randomUUID();
        UUID node = UUID.randomUUID();

        when(personalization.generateRecommendationsForCurrentStudent())
                .thenReturn(List.of(recommendation(withoutIds), recommendation(withIds)));
        when(personalization.acceptRecommendation(withoutIds))
                .thenReturn(RoadmapRecommendationDecisionResponse.builder().completedNodeCount(1).build());
        when(personalization.acceptRecommendation(withIds)).thenReturn(decision(node));

        assertEquals(List.of(node), trigger.refreshAndCollect("test"));
    }

    @Test
    void aNullDecisionIsSkipped() {
        UUID rec = UUID.randomUUID();
        when(personalization.generateRecommendationsForCurrentStudent())
                .thenReturn(List.of(recommendation(rec)));
        when(personalization.acceptRecommendation(any())).thenReturn(null);

        assertTrue(trigger.refreshAndCollect("test").isEmpty());
    }
}
