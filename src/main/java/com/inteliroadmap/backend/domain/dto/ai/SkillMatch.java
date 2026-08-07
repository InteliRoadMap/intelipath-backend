package com.inteliroadmap.backend.domain.dto.ai;

/**
 * A skill some source claims a student has, with how much that source is
 * trusted.
 *
 * <p>Lives here rather than nested inside one analyzer because several
 * unrelated producers emit it — a repository read by AI, a graded
 * self-assessment, and anything added later — and one consumer,
 * {@code SkillEvidenceService}, accepts all of them. Nesting it in the GitHub
 * analyzer forced the assessment code to import a portfolio class it has no
 * business knowing about.
 *
 * @param skill      a name that must exist in the skill catalog; anything else
 *                   is discarded rather than minted into a new skill
 * @param confidence 0..1, clamped per source by the evidence service — the
 *                   producer proposes, the evidence service decides the ceiling
 */
public record SkillMatch(String skill, double confidence) {}
