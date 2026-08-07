package com.inteliroadmap.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One option of a multiple-choice item.
 *
 * <p>{@code key} is a short stable token ({@code "a"}, {@code "b"}) rather than the
 * option's index, because the client shuffles nothing today but might, and an
 * answer keyed by position would silently start grading the wrong option the day
 * it does.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssessmentChoice(String key, String text) {}
