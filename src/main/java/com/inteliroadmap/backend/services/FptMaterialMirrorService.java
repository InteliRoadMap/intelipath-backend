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
     * @param subjectCode mirror only this subject, or null for every un-mirrored file
     * @param force       re-download files that were already mirrored
     */
    MirrorSummary mirrorMaterials(String subjectCode, boolean force);

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
