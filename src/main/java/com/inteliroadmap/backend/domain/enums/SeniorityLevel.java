package com.inteliroadmap.backend.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A career level, used for both sides of the same comparison: how far along a
 * student is, and which job postings sit at that height.
 *
 * <p>{@link #UNKNOWN} exists only for postings. A posting whose experience text
 * cannot be parsed is genuinely unknown, and hiding those would make the market
 * look smaller than it is — so they are labelled and kept. A student is never
 * UNKNOWN: either they took the assessment and have a level, or they did not and
 * have none at all, which is expressed by an absent value rather than by this.
 */
public enum SeniorityLevel {

    /**
     * Below FRESHER: the student cannot yet evidence the foundations of the role.
     *
     * <p>Not an insult and not a failure state — it is the honest answer for
     * someone starting out, and it is the only level that lets the roadmap open
     * with foundations instead of pretending they are already past them.
     */
    BEGINNER,
    FRESHER,
    JUNIOR,
    MID,
    SENIOR,
    /** Above SENIOR: near-complete, mostly verified coverage of the role. */
    EXPERT,
    UNKNOWN;

    /** Ladder order, lowest first. UNKNOWN is deliberately absent — it is not a rung. */
    public static final SeniorityLevel[] LADDER =
            {BEGINNER, FRESHER, JUNIOR, MID, SENIOR, EXPERT};

    @JsonValue
    public String toValue() {
        return name();
    }

    public static SeniorityLevel fromString(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return valueOf(raw.trim().toUpperCase());
    }

    /**
     * The lower of two levels, treating {@link #UNKNOWN} as "no opinion" so it
     * never drags a real level down.
     *
     * <p>Used to reconcile the model's verdict with the formula's: whichever is
     * more conservative wins, because this is unverified self-report and an
     * over-stated level costs the student more than an under-stated one.
     */
    public static SeniorityLevel min(SeniorityLevel a, SeniorityLevel b) {
        if (a == null || a == UNKNOWN) return b;
        if (b == null || b == UNKNOWN) return a;
        return a.ordinal() <= b.ordinal() ? a : b;
    }

    /**
     * The higher of two measured levels. UNKNOWN is no opinion, not a rung.
     * Used when fresh objective evidence may raise an assessment result but must
     * never erase what that completed assessment already demonstrated.
     */
    public static SeniorityLevel max(SeniorityLevel a, SeniorityLevel b) {
        if (a == null || a == UNKNOWN) return b;
        if (b == null || b == UNKNOWN) return a;
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
