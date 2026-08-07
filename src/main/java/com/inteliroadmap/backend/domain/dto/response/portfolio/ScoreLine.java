package com.inteliroadmap.backend.domain.dto.response.portfolio;

/**
 * One signal's contribution to a repository's portfolio quality score.
 *
 * @param label  what was measured, e.g. {@code Recent activity}
 * @param points what it added, {@code 0} included
 * @param max    the most this signal could have added
 * @param detail the underlying fact, e.g. {@code last push 5 day(s) ago}
 */
public record ScoreLine(String label, int points, int max, String detail) {}
