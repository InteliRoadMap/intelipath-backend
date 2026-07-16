package com.inteliroadmap.backend.services;

/**
 * Copies the course files referenced by a synced syllabus into our own private storage.
 *
 * We host the material rather than pointing at the source, so the FPT-only rule actually
 * withholds it: the source links are plain public URLs, and hiding a public link is a UX
 * choice, not a boundary. Mirroring is also what keeps a subject page working when the
 * upstream link rots.
 */
public interface FptMaterialMirrorService {

    /**
     * Starts a mirror in the background and returns its job id.
     *
     * Not inline: a full run is ~419 MB across 63 files, which no HTTP request should be
     * made to sit through. Callers poll {@link #poll}.
     *
     * @param subjectCode mirror only this subject, or null for every un-mirrored file
     * @param force       re-download files that were already mirrored
     */
    String start(String subjectCode, boolean force);

    /** Progress for a job id, or null once it is unknown (restart clears them). */
    MirrorJobStatus poll(String jobId);

    /**
     * @param state   pending | running | done | error
     * @param summary populated only in the terminal done state
     */
    record MirrorJobStatus(String state, String phase, int done, int total,
                           String message, MirrorSummary summary, String error) {
    }

    /**
     * @param attempted files that had a source to fetch
     * @param mirrored  files now stored and downloadable
     * @param skipped   already mirrored (and not forced)
     * @param failed    source fetch or upload failed; the row keeps its old state
     * @param bytes     total mirrored in this run
     */
    record MirrorSummary(int attempted, int mirrored, int skipped, int failed, long bytes) {
    }
}
