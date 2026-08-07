package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapRecommendationDecisionResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapRecommendationResponse;
import com.inteliroadmap.backend.services.RoadmapPersonalizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Re-runs roadmap personalization after new evidence lands, so the roadmap
 * reflects what the student just supplied without them having to press a button.
 *
 * <p>Importing a GitHub project used to record evidence and stop there: the
 * recommendation it justified stayed PENDING until the student happened to open
 * the roadmap and trigger a generate. From the student's side the import simply
 * did nothing. This closes that loop.
 *
 * <p>Deliberately swallows every failure. Personalization is a <em>consequence</em>
 * of the import, not part of it — a student whose repository was read correctly
 * must not see the import fail because the recommendation engine had a bad day.
 * The evidence is already persisted, so the next manual generate picks it up.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RoadmapRefreshTrigger {

    private final RoadmapPersonalizationService roadmapPersonalizationService;

    /**
     * Generates recommendations for the current student and applies them.
     *
     * <p>Auto-accepting is safe because it changes no gate: every importance
     * floor, proficiency requirement and completion policy still runs inside
     * {@code acceptRecommendation}. Evidence too weak to clear those floors
     * never becomes a recommendation in the first place.
     *
     * @param reason short label for the log, e.g. "github-import"
     * @return how many recommendations were applied
     */
    public int refreshCurrentStudent(String reason) {
        return refreshAndCollect(reason).size();
    }

    /**
     * The same refresh, but reporting <em>which</em> nodes were marked.
     *
     * <p>Added beside {@link #refreshCurrentStudent} rather than replacing it: the
     * three existing callers only ever wanted a count for their logs, and changing
     * their return type would be churn for no gain.
     *
     * <p>The ids exist so the roadmap can show the marking rather than merely
     * having happened. A student who submits an assessment and returns to an
     * unchanged-looking canvas has no way to tell "nothing qualified" from "the
     * feature is broken"; a tick that lands in front of them answers that.
     *
     * @param reason short label for the log, e.g. "graded-assessment"
     * @return node ids marked complete, in the order they were applied; never null
     */
    public List<UUID> refreshAndCollect(String reason) {
        List<UUID> markedNodeIds = new ArrayList<>();
        try {
            List<RoadmapRecommendationResponse> generated =
                    roadmapPersonalizationService.generateRecommendationsForCurrentStudent();
            if (generated.isEmpty()) {
                roadmapPersonalizationService.reconcileCompletedTopicsForCurrentStudent();
                log.debug("RoadmapRefreshTrigger: nothing new to apply after {}.", reason);
                return markedNodeIds;
            }
            int applied = 0;
            for (RoadmapRecommendationResponse recommendation : generated) {
                try {
                    RoadmapRecommendationDecisionResponse decision =
                            roadmapPersonalizationService.acceptRecommendation(recommendation.getRecommendationId());
                    applied++;
                    // Nodes actually marked, not recommendations accepted. An
                    // accepted recommendation whose every item was gated out marks
                    // nothing, and counting it would promise the student a tick
                    // that never appears.
                    if (decision != null && decision.getCompletedNodeIds() != null) {
                        markedNodeIds.addAll(decision.getCompletedNodeIds());
                    }
                } catch (Exception e) {
                    // One bad recommendation must not strand the others.
                    log.warn("RoadmapRefreshTrigger: could not apply recommendation {} after {}: {}",
                            recommendation.getRecommendationId(), reason, e.getMessage());
                }
            }
            log.info("RoadmapRefreshTrigger: applied {} of {} recommendation(s) after {}, marking {} node(s).",
                    applied, generated.size(), reason, markedNodeIds.size());
            roadmapPersonalizationService.reconcileCompletedTopicsForCurrentStudent();
            return markedNodeIds;
        } catch (Exception e) {
            log.warn("RoadmapRefreshTrigger: roadmap refresh after {} failed, evidence kept for the "
                    + "next manual generate: {}", reason, e.getMessage());
            return markedNodeIds;
        }
    }
}
