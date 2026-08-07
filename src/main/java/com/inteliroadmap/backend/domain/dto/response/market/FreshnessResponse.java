package com.inteliroadmap.backend.domain.dto.response.market;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class FreshnessResponse {
    private int windowDays;
    /** Distinct jobs advertised in the window. */
    private int jobsInWindow;
    /** Of those, the ones never advertised before — what "new" honestly means. */
    private int newJobs;
    /** Most recent posting date on file, so a stale scrape is visible rather than hidden. */
    private LocalDate latestPostedDate;
}
