package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin request to pull fresh FLM data. The {@code cookie} is a live FLM session
 * secret the admin pastes in; it is forwarded straight to the AI service and never
 * logged or persisted. Provide {@code curid} (for semesters) and/or {@code prefixes}
 * (for syllabi) — at least one is required.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminFlmSyncRequest {

    @NotBlank(message = "An FLM cookie is required")
    private String cookie;

    /**
     * FLM curriculum code this sync belongs to, e.g. "BIT_SE_K21B". Program, cohort and
     * batch are parsed from it so multiple cohort curricula coexist without overwriting.
     */
    private String curriculumCode;

    /** Curriculum id (curid) whose subjects carry the per-term semester numbers. */
    private String curid;

    /** Comma-separated subject-code prefixes to discover syllabi (e.g. "PRO,DBI,SWE"). */
    private String prefixes;

    /** Optional career filter for the coverage gap report (does not affect the import). */
    private String career;
}
