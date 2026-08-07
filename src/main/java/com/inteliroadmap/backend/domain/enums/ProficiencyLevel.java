package com.inteliroadmap.backend.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How well a student knows one skill.
 *
 * <p>The labels are behaviourally anchored rather than a bare 1-5 scale: a
 * student rating themselves "4 out of 5" is guessing at a scale nobody defined,
 * whereas "I used it in something real users touched" is a fact they either can
 * or cannot claim. That makes the answers far harder to inflate, and it gives
 * the AI adjudicator something concrete to check a written justification against.
 */
public enum ProficiencyLevel {

    /** Read about it or watched a tutorial; never written a line. */
    AWARE(1),
    /** Followed a tutorial and got it working. */
    PRACTICED(2),
    /** Used it in a project the student designed themselves. */
    APPLIED(3),
    /** Used it in something real users or a real team touched. */
    PROFESSIONAL(4);

    private final int score;

    ProficiencyLevel(int score) {
        this.score = score;
    }

    /** 1..4, the value stored in {@code student_skills.proficiency}. */
    public int getScore() {
        return score;
    }

    @JsonValue
    public String toValue() {
        return name();
    }

    /**
     * Parses an answer token. {@code null}, blank and {@code "NONE"} all mean
     * "the student does not have this skill" and map to null rather than to
     * AWARE — never having heard of something is not the same as being aware
     * of it, and the difference decides whether evidence is recorded at all.
     */
    public static ProficiencyLevel fromAnswer(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String token = raw.trim().toUpperCase();
        if ("NONE".equals(token)) return null;
        return valueOf(token);
    }

    /** Maps a stored 1..4 score back, or null when the column is null/out of range. */
    public static ProficiencyLevel fromScore(Integer score) {
        if (score == null) return null;
        for (ProficiencyLevel level : values()) {
            if (level.score == score) return level;
        }
        return null;
    }
}
