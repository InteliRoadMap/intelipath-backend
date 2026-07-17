package com.inteliroadmap.backend.domain.enums;

/**
 * How a student's FPT-subject record was set: inferred from "I finished term N"
 * (CURRICULUM_TERM) or ticked individually by the student (MANUAL). MANUAL wins
 * over an inferred row for the same subject.
 */
public enum StudentSubjectSource {
    CURRICULUM_TERM,
    MANUAL
}
