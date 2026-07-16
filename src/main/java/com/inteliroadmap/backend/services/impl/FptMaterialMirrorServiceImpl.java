package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.entity.FptSubjectResource;
import com.inteliroadmap.backend.repositories.FptSubjectResourceRepository;
import com.inteliroadmap.backend.services.FptMaterialMirrorService;
import com.inteliroadmap.backend.services.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FptMaterialMirrorServiceImpl implements FptMaterialMirrorService {

    /** Guards against a redirect to something enormous; the largest real file seen is ~14 MB. */
    private static final int MAX_FILE_BYTES = 64 * 1024 * 1024;

    private final FptSubjectResourceRepository fptSubjectResourceRepository;
    private final SupabaseStorageService supabaseStorageService;
    private final RestTemplate externalApiRestTemplate;

    @Override
    @Transactional
    public MirrorSummary mirrorMaterials(String subjectCode, boolean force) {
        List<FptSubjectResource> candidates = subjectCode == null || subjectCode.isBlank()
                ? fptSubjectResourceRepository.findMirrorCandidates()
                : fptSubjectResourceRepository.findMirrorCandidatesBySubject(subjectCode.trim());

        int attempted = 0;
        int mirrored = 0;
        int skipped = 0;
        int failed = 0;
        long bytes = 0;

        for (FptSubjectResource resource : candidates) {
            attempted++;
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
                // One dead link must not abandon the rest; the row keeps its previous state
                // so a later run retries it.
                log.warn("FptMaterialMirror: {} — failed to mirror: {}",
                        resource.getSubjectCode(), e.getMessage());
                failed++;
            }
        }

        log.info("FptMaterialMirror: attempted {}, mirrored {}, skipped {}, failed {} ({} bytes)",
                attempted, mirrored, skipped, failed, bytes);
        return new MirrorSummary(attempted, mirrored, skipped, failed, bytes);
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
