package com.inteliroadmap.backend.services.impl;

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
    private final Map<String, FlmSyncStatusResponse.Summary> importedJobs = new ConcurrentHashMap<>();
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
            FlmSyncStatusResponse.Summary summary = ensureImported(jobId, status.path("overlay"));
            response.setState("imported");
            response.setSummary(summary);
            if (summary != null) {
                response.setMessage(String.format(
                        "Imported %d subjects, %d skill links, %d resources.",
                        summary.getSubjects(), summary.getSkillLinks(), summary.getResources()));
            }
        }
        return response;
    }

    /** Runs the overlay import once per job id; later polls return the cached summary. */
    private synchronized FlmSyncStatusResponse.Summary ensureImported(String jobId, JsonNode overlay) {
        FlmSyncStatusResponse.Summary cached = importedJobs.get(jobId);
        if (cached != null) return cached;

        if (overlay == null || !overlay.isObject()) {
            log.warn("AdminFlmSync: job {} finished but returned no overlay to import.", jobId);
            return null;
        }
        FptOverlayImportService.CurriculumRef ref = jobCurricula.getOrDefault(
                jobId, new FptOverlayImportService.CurriculumRef("UNKNOWN", null, false));
        FptOverlayImportService.ImportSummary result = fptOverlayImportService.importOverlay(overlay, ref);
        FlmSyncStatusResponse.Summary summary = FlmSyncStatusResponse.Summary.builder()
                .subjects(result.subjects())
                .skillLinks(result.skillLinks())
                .unmatchedSkills(result.unmatchedSkills())
                .resources(result.resources())
                .build();
        importedJobs.put(jobId, summary);
        log.info("AdminFlmSync: imported overlay for job {} — {} subjects, {} resources.",
                jobId, result.subjects(), result.resources());
        return summary;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
