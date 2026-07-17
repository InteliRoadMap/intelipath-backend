package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.entity.FptSubjectResource;
import com.inteliroadmap.backend.repositories.FptSubjectResourceRepository;
import com.inteliroadmap.backend.services.FptMaterialMirrorService;
import com.inteliroadmap.backend.services.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class FptMaterialMirrorServiceImpl implements FptMaterialMirrorService {

    /**
     * Upper bound on a single file. The largest real syllabus file measured is 79 MB, so
     * this must stay well clear of it — a limit tuned to the "typical" ~5 MB file silently
     * drops exactly the biggest, most useful bundles.
     */
    private static final int MAX_FILE_BYTES = 128 * 1024 * 1024;

    /**
     * In-memory job store. Jobs are short-lived and polled by one admin screen, so this
     * matches the FLM sync it sits next to; a restart forgets them, which is why poll()
     * returning null must read as "unknown", not "failed".
     */
    private final Map<String, MirrorJobStatus> jobs = new ConcurrentHashMap<>();

    private final FptSubjectResourceRepository fptSubjectResourceRepository;
    private final SupabaseStorageService supabaseStorageService;
    private final RestTemplate externalApiRestTemplate;

    @Override
    public String start(String subjectCode, boolean force) {
        String jobId = UUID.randomUUID().toString().replace("-", "");
        jobs.put(jobId, new MirrorJobStatus("pending", "queued", 0, 0, "Queued", null, null));
        Thread worker = new Thread(() -> run(jobId, subjectCode, force), "flm-mirror-" + jobId);
        worker.setDaemon(true);
        worker.start();
        return jobId;
    }

    @Override
    public MirrorJobStatus poll(String jobId) {
        return jobs.get(jobId);
    }

    private void run(String jobId, String subjectCode, boolean force) {
        try {
            List<FptSubjectResource> candidates = subjectCode == null || subjectCode.isBlank()
                    ? fptSubjectResourceRepository.findMirrorCandidates()
                    : fptSubjectResourceRepository.findMirrorCandidatesBySubject(subjectCode.trim());

            int total = candidates.size();
            jobs.put(jobId, new MirrorJobStatus("running", "mirroring", 0, total,
                    "Found " + total + " files with a source.", null, null));

            int mirrored = 0;
            int skipped = 0;
            int failed = 0;
            long bytes = 0;

            for (int i = 0; i < total; i++) {
                FptSubjectResource resource = candidates.get(i);
                jobs.put(jobId, new MirrorJobStatus("running", "mirroring", i, total,
                        "Fetching " + resource.getSubjectCode() + " (" + (i + 1) + "/" + total + ")…",
                        null, null));

                if (!force && resource.getStoragePath() != null) {
                    skipped++;
                    continue;
                }
                try {
                    byte[] content = download(resource.getSourceUrl());
                    String path = objectPath(resource);
                    supabaseStorageService.uploadCourseMaterial(content, path, contentTypeFor(path));

                    resource.setStoragePath(path);
                    resource.setSizeBytes((long) content.length);
                    resource.setMirroredAt(LocalDateTime.now());
                    fptSubjectResourceRepository.save(resource);

                    mirrored++;
                    bytes += content.length;
                } catch (Exception e) {
                    // Expect failures: FLM's /file/download?scheduleId= handler currently
                    // 500s for every id, even for a logged-in browser. One dead source must
                    // not abandon the rest, and the row keeps its state so a later run retries.
                    log.warn("FptMaterialMirror: {} — failed to mirror: {}",
                            resource.getSubjectCode(), e.getMessage());
                    failed++;
                }
            }

            MirrorSummary summary = new MirrorSummary(total, mirrored, skipped, failed, bytes);
            jobs.put(jobId, new MirrorJobStatus("done", "done", total, total,
                    "Mirrored " + mirrored + " of " + total + " files.", summary, null));
            log.info("FptMaterialMirror {}: mirrored {}, skipped {}, failed {} ({} bytes)",
                    jobId, mirrored, skipped, failed, bytes);
        } catch (Exception e) {
            log.error("FptMaterialMirror {} failed", jobId, e);
            jobs.put(jobId, new MirrorJobStatus("error", "done", 0, 0, null, null, e.getMessage()));
        }
    }

    private byte[] download(String sourceUrl) {
        ResponseEntity<byte[]> response = externalApiRestTemplate.getForEntity(URI.create(sourceUrl), byte[].class);
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            throw new IllegalStateException("empty response from source");
        }
        if (body.length > MAX_FILE_BYTES) {
            throw new IllegalStateException("file exceeds " + MAX_FILE_BYTES + " bytes");
        }
        return body;
    }

    /**
     * {@code PRJ301/1_PRJ301.zip} — keyed by subject and the source's own file name, so a
     * re-mirror overwrites the same object instead of accumulating copies.
     */
    private String objectPath(FptSubjectResource resource) {
        String source = resource.getSourceUrl();
        String name = source.substring(source.lastIndexOf('/') + 1);
        int query = name.indexOf('?');
        if (query >= 0) name = name.substring(0, query);
        // Storage keys are path segments: anything exotic in the upstream name would
        // otherwise create folders or break the key.
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.isBlank()) name = resource.getId() + ".bin";
        return resource.getSubjectCode() + "/" + name;
    }

    private String contentTypeFor(String path) {
        return path.toLowerCase().endsWith(".zip") ? "application/zip" : "application/octet-stream";
    }
}
