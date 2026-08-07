package com.inteliroadmap.backend.domain.model;

import com.inteliroadmap.backend.domain.enums.ImportanceLevel;

import java.util.UUID;

/**
 * One skill the self-assessment asks about, as the domain sees it.
 *
 * <p>Separate from {@code AssessmentQuestionResponse} on purpose: the builder
 * that produces these and the service that grades against them are domain logic
 * and should not be pinned to whatever shape the HTTP layer happens to expose
 * this week. A mapper converts at the boundary, so changing the wire format
 * cannot ripple back into the grading rules.
 *
 * @param importance   how much the target career needs this skill, which is also
 *                     what orders the question set
 * @param noteRequired whether an APPLIED or PROFESSIONAL answer must be backed by
 *                     a written explanation — set only on the few most important
 *                     skills, since that prose is what the grading actually reads
 */
public record AssessmentQuestion(UUID skillId,
                                 String skillName,
                                 String category,
                                 ImportanceLevel importance,
                                 boolean noteRequired) {}
