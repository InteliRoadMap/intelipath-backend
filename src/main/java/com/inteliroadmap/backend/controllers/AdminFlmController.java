package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.AdminFlmSyncRequest;
import com.inteliroadmap.backend.domain.dto.response.admin.FlmSyncStartResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.FlmSyncStatusResponse;
import com.inteliroadmap.backend.services.AdminFlmSyncService;
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
import org.springframework.web.bind.annotation.RestController;

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
