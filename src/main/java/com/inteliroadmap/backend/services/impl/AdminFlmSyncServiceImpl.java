package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.response.admin.Summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.inteliroadmap.backend.ai.client.AiServiceClient;
import com.inteliroadmap.backend.domain.dto.request.AdminFlmSyncRequest;
import com.inteliroadmap.backend.domain.dto.response.admin.FlmSyncStartResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.FlmSyncStatusResponse;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.services.AdminFlmSyncService;
import com.inteliroadmap.backend.services.FptOverlayImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminFlmSyncServiceImpl implements AdminFlmSyncService {

    // A scrape can run for several minutes; watch it server-side so the import fires
    // even if the admin's browser closes or their session expires mid-run.
    private static final long WATCH_POLL_SECONDS = 5;
    private static final long WATCH_TIMEOUT_SECONDS = 30 * 60;

    private final AiServiceClient aiServiceClient;
    private final SkillRepository skillRepository;
    private final FptOverlayImportService fptOverlayImportService;

    // Import each finished job's overlay exactly once; subsequent polls read the cached
    // summary. In-memory and single-instance, which matches this admin-only tool.
    private final Map<String, Summary> importedJobs = new ConcurrentHashMap<>();
    // Which curriculum each running job belongs to, so its overlay imports under the right
    // code/curid whether the foreground poll or the server-side watcher completes it.
    private final Map<String, FptOverlayImportService.CurriculumRef> jobCurricula = new ConcurrentHashMap<>();
    private final ExecutorService watchers = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "flm-sync-watcher");
        t.setDaemon(true);
        return t;
    });

    @Override
    public FlmSyncStartResponse start(AdminFlmSyncRequest request) {
        boolean hasCurid = request.getCurid() != null && !request.getCurid().isBlank();
        boolean hasPrefixes = request.getPrefixes() != null && !request.getPrefixes().isBlank();
        if (!hasCurid && !hasPrefixes) {
            throw new IllegalArgumentException("Provide a curriculum id and/or subject prefixes.");
        }
        String curriculumCode = request.getCurriculumCode() == null ? "" : request.getCurriculumCode().trim();
        if (curriculumCode.isEmpty()) {
            throw new IllegalArgumentException("A curriculum code (e.g. BIT_SE_K21B) is required.");
        }

        // The backend owns the skill catalog; send the names so the AI service can map
        // course outcomes onto them without needing the CSV shared as a file.
        Set<String> catalog = new LinkedHashSet<>();
        for (Skill skill : skillRepository.findAll()) {
            if (skill.getSkillName() != null && !skill.getSkillName().isBlank()) {
                catalog.add(skill.getSkillName().trim());
            }
        }

        Map<String, Object> body = new HashMap<>();
        body.put("cookie", request.getCookie());
        body.put("curid", hasCurid ? request.getCurid().trim() : null);
        body.put("prefixes", hasPrefixes ? request.getPrefixes().trim() : null);
        body.put("career", request.getCareer());
        body.put("catalog", List.copyOf(catalog));

        String jobId = aiServiceClient.startFlmSync(body);
        // Never log the cookie; the code/curid/prefixes are safe to log.
        log.info("AdminFlmSync: started job {} (curriculum={}, curid={}, prefixes={}, catalog={} skills)",
                jobId, curriculumCode, body.get("curid"), body.get("prefixes"), catalog.size());
        if (jobId != null) {
            jobCurricula.put(jobId, new FptOverlayImportService.CurriculumRef(
                    curriculumCode, hasCurid ? request.getCurid().trim() : null, false));
            watch(jobId);
        }
        return FlmSyncStartResponse.builder().jobId(jobId).build();
    }

    /**
     * Poll the job server-side until it finishes and import the overlay, so a long
     * scrape completes even if the admin closes the tab or gets logged out. Idempotent
     * with the foreground poll via {@link #ensureImported} — whichever reaches "done"
     * first imports; the other is a no-op.
     */
    private void watch(String jobId) {
        watchers.submit(() -> {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WATCH_TIMEOUT_SECONDS);
            while (System.nanoTime() < deadline) {
                try {
                    TimeUnit.SECONDS.sleep(WATCH_POLL_SECONDS);
                    JsonNode status = aiServiceClient.getFlmSyncStatus(jobId);
                    String state = status == null ? "" : status.path("state").asText("");
                    if ("done".equals(state)) {
                        ensureImported(jobId, status.path("overlay"));
                        return;
                    }
                    if ("error".equals(state)) {
                        log.warn("AdminFlmSync: watcher stopping — job {} failed: {}",
                                jobId, text(status, "error"));
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    // Transient (e.g. the AI service restarted and lost the job): log and stop.
                    log.warn("AdminFlmSync: watcher for job {} stopped: {}", jobId, e.getMessage());
                    return;
                }
            }
            log.warn("AdminFlmSync: watcher for job {} timed out after {}s.", jobId, WATCH_TIMEOUT_SECONDS);
        });
    }

    @Override
    public FlmSyncStatusResponse poll(String jobId) {
        JsonNode status = aiServiceClient.getFlmSyncStatus(jobId);
        if (status == null) {
            throw new ResourceNotFoundException("FLM sync job not found: " + jobId);
        }

        String state = status.path("state").asText("");
        FlmSyncStatusResponse response = FlmSyncStatusResponse.builder()
                .state(state)
                .phase(status.path("phase").asText(""))
                .done(status.path("done").asInt(0))
                .total(status.path("total").asInt(0))
                .message(text(status, "message"))
                .error(text(status, "error"))
                .build();

        if ("done".equals(state)) {
            Summary summary = ensureImported(jobId, status.path("overlay"));
            response.setState("imported");
            response.setSummary(summary);
            if (summary != null && summary.isSuspect()) {
                // Say what went wrong, not just what was counted: the admin's next move is to
                // fetch a fresh cookie, and a cheerful "imported 47 subjects" hides that.
                response.setMessage(String.format(
                        "Found %d subjects but no outcomes, skills or files for any of them. "
                                + "The FLM cookie has most likely expired — existing data was kept "
                                + "untouched. Re-run with a fresh cookie.",
                        summary.getSubjects()));
            } else if (summary != null) {
                response.setMessage(String.format(
                        "Imported %d subjects, %d skill links, %d outcomes, %d resources.",
                        summary.getSubjects(), summary.getSkillLinks(),
                        summary.getClos(), summary.getResources()));
            }
        }
        return response;
    }

    /** Runs the overlay import once per job id; later polls return the cached summary. */
    private synchronized Summary ensureImported(String jobId, JsonNode overlay) {
        Summary cached = importedJobs.get(jobId);
        if (cached != null) return cached;

        if (overlay == null || !overlay.isObject()) {
            log.warn("AdminFlmSync: job {} finished but returned no overlay to import.", jobId);
            return null;
        }
        FptOverlayImportService.CurriculumRef ref = jobCurricula.getOrDefault(
                jobId, new FptOverlayImportService.CurriculumRef("UNKNOWN", null, false));
        FptOverlayImportService.ImportSummary result = fptOverlayImportService.importOverlay(overlay, ref);
        Summary summary = Summary.builder()
                .subjects(result.subjects())
                .skillLinks(result.skillLinks())
                .unmatchedSkills(result.unmatchedSkills())
                .resources(result.resources())
                .clos(result.clos())
                .preservedSubjects(result.preservedSubjects())
                .suspect(isSuspect(result))
                .build();
        importedJobs.put(jobId, summary);
        if (summary.isSuspect()) {
            log.warn("AdminFlmSync: job {} imported {} subjects but no skills, CLOs or resources for any "
                            + "of them — the scrape most likely failed to authenticate. Existing detail was "
                            + "left untouched; re-run with a fresh FLM cookie.",
                    jobId, result.subjects());
        } else {
            log.info("AdminFlmSync: imported overlay for job {} — {} subjects, {} CLOs, {} resources "
                            + "({} subject(s) carried no detail and kept what was stored).",
                    jobId, result.subjects(), result.clos(), result.resources(), result.preservedSubjects());
        }
        return summary;
    }

    /**
     * A scrape that returned subject shells and nothing else. The scraper reaches the
     * curriculum listing without a valid session but cannot open any syllabus, so "subjects
     * found, detail empty" is what an expired cookie looks like — not a real curriculum.
     * Worth flagging loudly, because the import itself succeeds and would otherwise be
     * reported to the admin as a clean run.
     */
    private static boolean isSuspect(FptOverlayImportService.ImportSummary result) {
        return result.subjects() > 0
                && result.skillLinks() == 0
                && result.clos() == 0
                && result.resources() == 0;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
