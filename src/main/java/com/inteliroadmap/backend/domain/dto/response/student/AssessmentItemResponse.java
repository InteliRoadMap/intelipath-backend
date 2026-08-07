package com.inteliroadmap.backend.domain.dto.response.student;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One graded question as the client sees it.
 *
 * <p><b>The answer key is not here, and must never be.</b> This DTO is what the
 * browser receives; {@code AssessmentItem} — the domain record — carries
 * {@code answer} and {@code rubric}, and the mapper deliberately does not copy
 * them. Grading happens on the server against the paper loaded from the classpath,
 * never against anything the client sends back.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentItemResponse {
    private String id;
    /** SINGLE_CHOICE | MULTI_CHOICE | SHORT_ANSWER | CODE. */
    private String kind;
    /** 1, 2 or 3 — shown so the student can see the paper get harder, not hidden. */
    private int tier;
    private String topic;
    private String prompt;
    /** Null for the written kinds. */
    private List<AssessmentChoiceResponse> choices;
    /** Language hint for the editor, e.g. "java", "typescript". CODE items only. */
    private String language;
    /** Code the student edits rather than starts from nothing. CODE items only. */
    private String starterCode;
    /** Points on offer, so a student can see a code question is worth more. */
    private int points;
}
