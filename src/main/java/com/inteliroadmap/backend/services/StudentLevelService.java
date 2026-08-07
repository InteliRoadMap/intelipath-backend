package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.student.StudentLevelResponse;

import java.util.Optional;

/**
 * The single place a student's career level is resolved.
 *
 * <p>Three features adapt to it — the roadmap, Market Pulse and the AI mentor —
 * so it is computed once here rather than three slightly different ways. The
 * level is derived from the student's skill rows on every call, not read from a
 * frozen column, so evidence added after the assessment (a connected repository,
 * an uploaded transcript) moves it without anyone re-taking anything.
 */
public interface StudentLevelService {

    /**
     * The authenticated student's level, or empty when they skipped the
     * assessment.
     *
     * <p>Empty means "no claim", which is not the same as FRESHER: a consumer
     * must fall back to its pre-assessment behaviour rather than treat the
     * student as a beginner. Never throws — a level is an enhancement, and a
     * failure to compute one must not take a page down with it.
     */
    Optional<StudentLevelResponse> currentLevel();

    /** As {@link #currentLevel()}, for a specific student. */
    Optional<StudentLevelResponse> levelOf(java.util.UUID userId);
}
