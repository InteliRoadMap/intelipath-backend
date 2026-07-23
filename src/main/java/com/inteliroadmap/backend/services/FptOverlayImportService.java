package com.inteliroadmap.backend.services;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Imports an FLM curriculum overlay (subjects + the catalog skills they cover +
 * their lesson resources) into the reference tables. Shared by the startup
 * {@code DatabaseSeeder} (reads a file) and the admin FLM-sync feature (imports the
 * overlay returned live by the AI service), so both go through the exact same,
 * idempotent import logic.
 */
public interface FptOverlayImportService {

    /**
     * Identity of the curriculum an overlay belongs to. Parsed from the FLM curriculum
     * code (e.g. "BIT_SE_K21B") plus the numeric curid used to scrape it.
     */
    record CurriculumRef(String code, String curid, boolean makeDefault) {}

    /**
     * Counts reported back after an import, for logging and the admin UI.
     *
     * <p>{@code preservedSubjects} counts subjects the overlay described but carried no
     * skills, CLOs or resources for, whose existing rows were therefore left alone. A high
     * count next to a low {@code resources} is the signature of a scrape that authenticated
     * but came back empty, which is worth surfacing rather than reporting as a clean import.
     */
    record ImportSummary(int subjects, int skillLinks, int unmatchedSkills, int resources,
                         int clos, int preservedSubjects) {}

    /**
     * Upsert every subject in {@code root.subjects[]} into the shared catalog, rebuild
     * each subject's skill links and resources, and (re)build the term mapping for the
     * curriculum identified by {@code ref} — WITHOUT deleting other curricula's subjects
     * or mappings. Never touches {@code student_fpt_subjects}, so student declarations
     * survive. Idempotent: safe to run on every restart or admin sync.
     *
     * <p>A facet (skills, CLOs, resources) is rebuilt only when the overlay actually carries
     * that facet for the subject. An overlay that says nothing about a subject's CLOs leaves
     * the stored CLOs alone rather than clearing them, because a scrape returning nothing is
     * far more often a broken scrape than a syllabus that genuinely lost its outcomes. The
     * cost is that a facet deleted upstream survives here until an overlay that does carry
     * that facet replaces it — stale data being much cheaper to fix than deleted data.
     */
    ImportSummary importOverlay(JsonNode root, CurriculumRef ref);
}
