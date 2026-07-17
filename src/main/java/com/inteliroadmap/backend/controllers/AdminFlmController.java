package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.AdminFlmSyncRequest;
import com.inteliroadmap.backend.domain.dto.response.admin.FlmSyncStartResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.FlmSyncStatusResponse;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.services.AdminFlmSyncService;
import com.inteliroadmap.backend.services.FptMaterialMirrorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin FLM-sync endpoints. The admin pastes a live FLM session cookie and triggers
 * a scrape that refreshes the FPT curriculum overlay (subjects, semesters, skill
 * coverage and lesson resources). The scrape runs asynchronously on the AI service;
 * the client polls {@code GET /sync/{jobId}} for progress and, once finished, the
 * backend imports the overlay in place. The cookie is never logged or persisted.
 */
@RestController
@RequestMapping("/api/v1/admin/flm")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin FLM Sync", description = "Pull fresh FPT curriculum data from FLM")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFlmController {

    private final AdminFlmSyncService adminFlmSyncService;
    private final FptMaterialMirrorService fptMaterialMirrorService;

    /**
     * Copies the files a sync discovered into our own storage, so students download from us.
     *
     * Separate from /sync on purpose: a sync only needs the syllabus HTML, while this pulls
     * tens of MB and can be re-run on its own when a file fails or an upstream copy changes.
     * No cookie is involved — the source files are public.
     */
    @PostMapping("/mirror-materials")
    @Operation(summary = "Start mirroring FPT course materials into storage",
            description = "Kicks off a background copy of the files referenced by synced syllabi. "
                    + "Returns a jobId to poll. Omit subjectCode to do every un-mirrored file.")
    public ResponseEntity<Map<String, String>> mirrorMaterials(
            @RequestParam(required = false) String subjectCode,
            @RequestParam(defaultValue = "false") boolean force) {
        log.info("AdminFlmController: material mirror requested (subjectCode={}, force={})",
                subjectCode, force);
        String jobId = fptMaterialMirrorService.start(subjectCode, force);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId));
    }

    @GetMapping("/mirror-materials/{jobId}")
    @Operation(summary = "Poll a material mirror",
            description = "Returns progress; the terminal 'done' state carries the summary.")
    public ResponseEntity<FptMaterialMirrorService.MirrorJobStatus> pollMirror(@PathVariable String jobId) {
        FptMaterialMirrorService.MirrorJobStatus status = fptMaterialMirrorService.poll(jobId);
        if (status == null) {
            // Jobs live in memory, so an unknown id usually means a restart rather than a
            // bad id; say so instead of implying the mirror failed.
            throw new ResourceNotFoundException("No mirror job " + jobId + " (the server may have restarted)");
        }
        return ResponseEntity.ok(status);
    }

    @PostMapping("/sync")
    @Operation(summary = "Start an FLM sync",
            description = "Kicks off a background scrape using the admin-supplied cookie. Returns a jobId to poll.")
    public ResponseEntity<FlmSyncStartResponse> startSync(@RequestBody @Valid AdminFlmSyncRequest request) {
        log.info("AdminFlmController: FLM sync requested (curid={}, prefixes={})",
                request.getCurid(), request.getPrefixes());
        return ResponseEntity.accepted().body(adminFlmSyncService.start(request));
    }

    @GetMapping("/sync/{jobId}")
    @Operation(summary = "Poll an FLM sync",
            description = "Returns progress; the terminal 'imported' state carries the import summary.")
    public ResponseEntity<FlmSyncStatusResponse> pollSync(@PathVariable String jobId) {
        return ResponseEntity.status(HttpStatus.OK).body(adminFlmSyncService.poll(jobId));
    }
}
