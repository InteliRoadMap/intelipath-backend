package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.entity.SkillNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * How much of a roadmap a student at a given level should be shown.
 *
 * <p><b>The problem.</b> Java's roadmap is 71 nodes and every student saw all
 * 71, including the ones that only mean anything after a year of writing Java.
 * A roadmap identical for a first-year and a final-year student is a syllabus,
 * and it was the specific complaint that got this project turned down once.
 *
 * <p><b>Shown, not deleted.</b> Nodes above a student's ceiling are returned
 * with {@code tierLocked = true} rather than dropped. Hiding them would leave
 * the student unable to see that the road continues — and unable to tell a
 * roadmap that ends at node 12 from one that has more waiting. A locked node is
 * a promise; a missing node is a shorter roadmap.
 *
 * <p><b>The ceiling is deliberately generous.</b> One tier above the student's
 * own is fully active, because the next thing you learn is by definition the
 * thing you cannot do yet. Only the tier beyond that locks.
 */
@Component
@Slf4j
public class RoadmapTierResolver {

    /** Beginner content: roots and their immediate topics. */
    public static final short TIER_BEGINNER = 1;
    /** Intermediate: one refinement deeper. */
    public static final short TIER_INTERMEDIATE = 2;
    /** Advanced: everything below that. */
    public static final short TIER_ADVANCED = 3;

    /**
     * The highest tier a student sees unlocked.
     *
     * <p>Six seniority bands map onto three tiers, so the mapping is stated here
     * rather than left to arithmetic on an ordinal: adding a band later must not
     * silently shift what everyone else can see.
     */
    public short ceilingFor(String level) {
        String band = level == null ? "" : level.trim().toUpperCase(Locale.ROOT);
        return switch (band) {
            case "BEGINNER", "FRESHER" -> TIER_INTERMEDIATE;
            case "JUNIOR", "MID" -> TIER_ADVANCED;
            case "SENIOR", "EXPERT" -> TIER_ADVANCED;
            // No level yet — an unassessed student must not be handed the
            // smallest roadmap, because "we do not know" is not "you are a
            // beginner". They get everything, same as today.
            default -> TIER_ADVANCED;
        };
    }

    /**
     * True when this node sits above what the student's level unlocks.
     *
     * <p>A node with no tier is never locked: NULL means "not graded", and
     * withholding content on the strength of a missing value is how a data gap
     * turns into a shorter roadmap nobody ordered.
     */
    public boolean isLocked(SkillNode node, short ceiling) {
        Short tier = node == null ? null : node.getTier();
        return tier != null && tier > ceiling;
    }

    /**
     * How many of these nodes the student's level unlocks — the number that has
     * to change when the level changes, or the tier column is doing nothing.
     */
    public long activeCount(List<SkillNode> nodes, short ceiling) {
        return nodes == null ? 0 : nodes.stream().filter(node -> !isLocked(node, ceiling)).count();
    }
}
