package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.DeclareCurriculumTermRequest;
import com.inteliroadmap.backend.domain.dto.request.SetStudentCurriculumRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateFptSubjectsRequest;
import com.inteliroadmap.backend.domain.dto.response.roadmap.FptSubjectDetailResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.StudentCurriculumResponse;
import com.inteliroadmap.backend.services.RoadmapPersonalizationService;
import com.inteliroadmap.backend.domain.entity.FptSubjectResource;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.FptSubjectResourceRepository;
import com.inteliroadmap.backend.services.SupabaseStorageService;
import com.inteliroadmap.backend.services.StudentCurriculumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Student-facing FPT curriculum declaration. Declaring finished subjects (by term or
 * individually) seeds transcript evidence and returns any freshly generated roadmap
 * recommendations so the UI can prompt to apply them.
 *
 * Restricted to FPT accounts: the whole flow is about a student's own FPT coursework,
 * so it has nothing to offer anyone else.
 */
@RestController
@RequestMapping("/api/v1/students/me")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Student Curriculum", description = "Declare completed FPT subjects to personalize the roadmap")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('STUDENT') and hasAuthority('ACCOUNT_FPT')")
public class StudentCurriculumController {

    /** Long enough to start a 14 MB download, short enough that a leaked link is worthless. */
    private static final int DOWNLOAD_LINK_TTL_SECONDS = 120;

    private final StudentCurriculumService studentCurriculumService;
    private final RoadmapPersonalizationService roadmapPersonalizationService;
    private final FptSubjectResourceRepository fptSubjectResourceRepository;
    private final SupabaseStorageService supabaseStorageService;

    /**
     * Mints a short-lived signed URL for one mirrored course file.
     *
     * The class-level rule is the gate, and it bites here because we serve our own copy
     * from a private bucket — the link expires, and no upstream URL is ever handed out.
     *
     * Returns the link as JSON rather than a 302: this API is called by a bearer-token
     * SPA, and a redirect would have the client replay our Authorization header at
     * Supabase's origin. The caller opens the link itself.
     */
    @GetMapping("/fpt-materials/{resourceId}/download")
    @Operation(summary = "Get a download link for an FPT course material",
            description = "Returns a short-lived signed link to the stored file.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Signed link"),
            @ApiResponse(responseCode = "403", description = "Not an FPT account"),
            @ApiResponse(responseCode = "404", description = "No such material, or it has no stored file")
    })
    public ResponseEntity<Map<String, Object>> downloadMaterial(@PathVariable UUID resourceId) {
        FptSubjectResource resource = fptSubjectResourceRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Material not found: " + resourceId));

        if (resource.getStoragePath() == null || resource.getStoragePath().isBlank()) {
            // Not mirrored yet: 7 of 28 subjects publish no files at all, so this is a
            // normal answer rather than an error to shout about.
            throw new ResourceNotFoundException(
                    "No file stored for this material yet: " + resource.getSubjectCode());
        }

        String signedUrl = supabaseStorageService.signCourseMaterial(
                resource.getStoragePath(), DOWNLOAD_LINK_TTL_SECONDS);
        log.info("StudentCurriculumController: signed download for {} ({})",
                resource.getSubjectCode(), resourceId);
        return ResponseEntity.ok(Map.of(
                "downloadUrl", signedUrl,
                "expiresInSeconds", DOWNLOAD_LINK_TTL_SECONDS));
    }

    @GetMapping("/fpt-subjects/{subjectCode}")
    @Operation(summary = "Get one FPT subject's syllabus detail",
            description = "Outcomes (CLOs), reference list and sessions, with which sessions have a stored file. "
                    + "Any subject the school teaches — not limited to the student's own combo.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = FptSubjectDetailResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not an FPT account"),
            @ApiResponse(responseCode = "404", description = "No such subject")
    })
    public ResponseEntity<FptSubjectDetailResponse> getSubjectDetail(@PathVariable String subjectCode) {
        return ResponseEntity.ok(studentCurriculumService.getSubjectDetail(subjectCode));
    }

    @GetMapping("/fpt-subjects")
    @Operation(summary = "Get FPT subject checklist",
            description = "The full FPT subject list annotated with which ones the student has declared as passed.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentCurriculumResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<StudentCurriculumResponse> getCurriculum() {
        return ResponseEntity.ok(studentCurriculumService.getCurriculum());
    }

    @PutMapping("/curriculum")
    @Operation(summary = "Set curriculum version",
            description = "Override which FLM curriculum version (cohort/program) the student follows.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentCurriculumResponse.class))),
            @ApiResponse(responseCode = "404", description = "Curriculum not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<StudentCurriculumResponse> setCurriculum(
            @Valid @RequestBody SetStudentCurriculumRequest request) {
        return ResponseEntity.ok(studentCurriculumService.setCurriculum(request));
    }

    @PutMapping("/curriculum-term")
    @Operation(summary = "Declare completed term",
            description = "Mark every FPT subject up to the given term as passed, then regenerate roadmap recommendations.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentCurriculumResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid term or student has no career selected"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<StudentCurriculumResponse> declareTerm(
            @Valid @RequestBody DeclareCurriculumTermRequest request) {
        StudentCurriculumResponse response = studentCurriculumService.applyCurriculumTerm(request);
        return ResponseEntity.ok(withRecommendations(response));
    }

    @PutMapping("/fpt-subjects")
    @Operation(summary = "Update passed FPT subjects",
            description = "Tick/untick individual FPT subjects, then regenerate roadmap recommendations.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentCurriculumResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or student has no career selected"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<StudentCurriculumResponse> updateSubjects(
            @Valid @RequestBody UpdateFptSubjectsRequest request) {
        StudentCurriculumResponse response = studentCurriculumService.updateSubjects(request);
        return ResponseEntity.ok(withRecommendations(response));
    }

    /**
     * Generate fresh recommendations in a separate transaction so a recommendation
     * failure never rolls back the (already committed) subject declaration.
     */
    private StudentCurriculumResponse withRecommendations(StudentCurriculumResponse response) {
        try {
            response.setRecommendations(roadmapPersonalizationService.generateRecommendationsForCurrentStudent());
        } catch (Exception e) {
            log.warn("StudentCurriculumController: recommendation generation skipped: {}", e.getMessage());
        }
        return response;
    }
}
