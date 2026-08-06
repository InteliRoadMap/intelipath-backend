package com.inteliroadmap.backend.domain.enums;

/**
 * What kind of answer an assessment item expects, which is also what decides who
 * grades it.
 *
 * <p>The split is deliberate and load-bearing. {@link #SINGLE_CHOICE} and
 * {@link #MULTI_CHOICE} are graded against an answer key in code — the same input
 * gives the same score forever, and the objective part of a student's level never
 * depends on a model being available or being in a good mood. {@link #SHORT_ANSWER}
 * and {@link #CODE} are graded by the LLM against a rubric written in the question
 * bank, so the model is comparing prose to stated criteria rather than inventing
 * what "good" means.
 */
public enum AssessmentItemKind {

    /** Exactly one correct option. Auto-graded. */
    SINGLE_CHOICE(true),

    /** Several correct options; partial credit is not given — all or nothing. */
    MULTI_CHOICE(true),

    /** A few sentences of prose, graded against a rubric. */
    SHORT_ANSWER(false),

    /** A function or a fix, graded against a rubric. */
    CODE(false);

    private final boolean autoGraded;

    AssessmentItemKind(boolean autoGraded) {
        this.autoGraded = autoGraded;
    }

    /** True when an answer key settles this item without asking a model. */
    public boolean isAutoGraded() {
        return autoGraded;
    }
}
