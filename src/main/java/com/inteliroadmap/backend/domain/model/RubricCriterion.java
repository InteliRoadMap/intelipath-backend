package com.inteliroadmap.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One thing the grader is told to look for in a written or code answer.
 *
 * <p><b>Why the rubric is authored and not left to the model.</b> "Grade this
 * answer out of 10" makes the model invent the criteria, which means two students
 * writing the same answer a week apart can be graded against different standards
 * and neither can be told why. Stating the criteria in the bank turns the model's
 * job into a comparison — did the answer do this specific thing — and makes the
 * result quotable back to the student.
 *
 * @param points what this criterion is worth; the item's total is the sum
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RubricCriterion(String criterion, int points) {}
