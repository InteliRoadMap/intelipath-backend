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

    /** Counts reported back after an import, for logging and the admin UI. */
    record ImportSummary(int subjects, int skillLinks, int unmatchedSkills, int resources) {}

    /**
     * Upsert every subject in {@code root.subjects[]} into the shared catalog, rebuild
     * each subject's skill links and resources, and (re)build the term mapping for the
     * curriculum identified by {@code ref} — WITHOUT deleting other curricula's subjects
     * or mappings. Never touches {@code student_fpt_subjects}, so student declarations
     * survive. Idempotent: safe to run on every restart or admin sync.
     */
    ImportSummary importOverlay(JsonNode root, CurriculumRef ref);
}
