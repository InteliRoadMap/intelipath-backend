package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.components.RoadmapRefreshTrigger;
import com.inteliroadmap.backend.domain.dto.request.ImportSkillsRequest;
import com.inteliroadmap.backend.domain.dto.request.SetupStudentProfileRequest;
import com.inteliroadmap.backend.domain.dto.request.SubmitAssessmentRequest;
import com.inteliroadmap.backend.domain.dto.request.SubmitGradedAssessmentRequest;
import com.inteliroadmap.backend.domain.dto.response.student.GradedAssessmentPaperResponse;
import com.inteliroadmap.backend.domain.dto.response.student.GradedAssessmentResultResponse;
import com.inteliroadmap.backend.services.GradedAssessmentService;
import com.inteliroadmap.backend.domain.dto.request.TargetCareerRequest;
import com.inteliroadmap.backend.domain.dto.response.roadmap.SkillResponse;
import com.inteliroadmap.backend.domain.dto.response.student.AssessmentQuestionSetResponse;
import com.inteliroadmap.backend.domain.dto.response.student.CareerAffinityResponse;
import com.inteliroadmap.backend.domain.dto.response.student.StudentAssessmentResultResponse;
import com.inteliroadmap.backend.domain.dto.response.student.StudentLevelResponse;
import com.inteliroadmap.backend.domain.dto.response.student.StudentResponse;
import com.inteliroadmap.backend.services.SkillService;
import com.inteliroadmap.backend.services.StudentAssessmentService;
import com.inteliroadmap.backend.services.StudentLevelService;
import com.inteliroadmap.backend.services.CareerAffinityService;
import com.inteliroadmap.backend.services.StudentService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller responsible for handling student-related API endpoints.
 * Provides functionality for profile setup and skill management.
 */
@RestController
@RequestMapping("/api/v1/student")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Student services", description = "Student endpoints")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('STUDENT')")
public class StudentController {

    private final StudentService studentService;
    private final SkillService skillService;
    private final StudentAssessmentService studentAssessmentService;
    private final GradedAssessmentService gradedAssessmentService;
    private final StudentLevelService studentLevelService;
    private final CareerAffinityService careerAffinityService;
    private final RoadmapRefreshTrigger roadmapRefreshTrigger;

    /**
     * Retrieves the information of a specific student.
     * @return ResponseEntity containing student's information
     */
    @GetMapping("/profile")
    @Operation(
            summary = "Get student profile",
            description = "Get student profile information"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Get student profile successful",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = StudentResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid access token"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            )
    })
    public ResponseEntity<StudentResponse> getStudentProfile() {
        log.info("StudentController: Student profile retrieval request received");
        return ResponseEntity.ok(studentService.getStudentProfile());
    }

    /**
     * Sets up or updates the profile information for a student.
     *
     * @param setupStudentProfileRequest The payload containing student profile details
     * @return ResponseEntity containing the updated user information
     */
    @PatchMapping("/profile")
    @Operation(
            summary = "Setup student profile",
            description = "User setup student profile"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Setup profile successful",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = StudentResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid student profile payload"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid access token"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User or career role not found"
            )
    })
    public ResponseEntity<StudentResponse> setupStudentProfile(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Student Profile payload",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SetupStudentProfileRequest.class)
                    )
            )
            @RequestBody @Valid SetupStudentProfileRequest setupStudentProfileRequest
        ) {
        log.info("StudentController: Student profile setup request received");
        return ResponseEntity.ok(studentService.setupStudentProfile(setupStudentProfileRequest));
    }

    @PostMapping(value = "/profile/transcript", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload student transcript for AI processing",
            description = "Upload transcript PDF, which will be processed by RAG for personalized Virtual Mentor insights"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Transcript uploaded and processed successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = StudentResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid file or file type"
            )
    })
    public ResponseEntity<StudentResponse> uploadTranscript(
            @RequestParam("file") MultipartFile file
    ) {
        log.info("StudentController: Student transcript upload request received");
        return ResponseEntity.ok(studentService.uploadTranscript(file));
    }

    @Deprecated
    @PutMapping("/target-career")
    @Operation(
            summary = "Update target career (DEPRECATED)",
            description = "DEPRECATED: Please use PATCH /api/v1/student/profile instead. Update the authenticated student's target career using a database career UUID",
            deprecated = true
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Target career updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = StudentResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid career ID"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid access token"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Student or career role not found"
            )
    })
    public ResponseEntity<StudentResponse> updateTargetCareer(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Target career request payload",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TargetCareerRequest.class)
                    )
            )
            @RequestBody @Valid TargetCareerRequest request
    ) {
        log.warn("StudentController: DEPRECATED API CALLED: PUT /target-career. Please migrate to PATCH /profile.");
        return ResponseEntity.ok(studentService.updateTargetCareer(request.getCareerId()));
    }


    /**
     * Retrieves the authenticated student's selected skills and all available skills.
     *
     * @return response containing selected skills and all skills in the database
     */
    @GetMapping("/skills")
    @Operation(
            summary = "Get selected and available skills",
            description = "Get the authenticated student's selected skills and all available skills"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Get student skills successful",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SkillResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid access token"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Student skills not found"
            )
    })
    public ResponseEntity<SkillResponse> getStudentSkills() {
        log.info("StudentController: Fetching selected and available skills for the authenticated student");
        return ResponseEntity.ok(skillService.getStudentSkills());
    }

    /**
     * Searches available skills by skill name without case sensitivity.
     *
     * @param search skill name fragment to search for
     * @return response containing matching skills
     */
    @GetMapping("/skills/search")
    @Operation(
            summary = "Search skills by name",
            description = "Search skills by skill name without case sensitivity"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Skill search completed successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SkillResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid access token"
            )
    })
    public ResponseEntity<SkillResponse> searchSkills(@RequestParam String keyword) {
        log.info("StudentController: Searching skills by name: {}", keyword);

        return ResponseEntity.ok(skillService.searchSkills(keyword));
    }

    /**
     * Imports a list of selected skills and associates them with the student.
     *
     * @param importSkillsRequest The payload containing selected skill IDs
     * @return ResponseEntity containing the updated list of the student's skills
     */
    @PostMapping("/skills/select")
    @Operation(
            summary = "Selected student skills",
            description = "Student selects their current skills"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Skills imported successful",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SkillResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid skill selection payload"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized or invalid access token"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "User or skill not found"
            )
    })
    public ResponseEntity<SkillResponse> importStudentSkills(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Imported Student Skills payload",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ImportSkillsRequest.class)
                    )
            )
            @RequestBody @Valid ImportSkillsRequest importSkillsRequest
    ) {
        log.info("StudentController: Importing selected skills for authenticated student");
        SkillResponse response = skillService.importStudentSkills(importSkillsRequest);
        // Declaring a skill is the student telling us what they already know, so the
        // roadmap should stop asking them to learn it. Run here rather than inside
        // importStudentSkills because that method is @Transactional: a single bad
        // recommendation marking the transaction rollback-only would take the whole
        // declaration down with it, and losing what the student typed to fix a tick
        // is the wrong trade. GithubPortfolioServiceImpl.importFromGithub already
        // sits outside its own transaction for the same reason.
        if (response != null) {
            response.setMarkedNodeIds(roadmapRefreshTrigger.refreshAndCollect("skill-declaration"));
        }
        return ResponseEntity.ok(response);
    }

    // ── Career self-assessment ──────────────────────────────────────────────
    // Optional: a student may skip it entirely, and everything downstream is
    // required to work without it. That is why the two read endpoints answer
    // with 204 rather than 404 when there is nothing yet — "not taken" is a
    // normal state, not a missing resource.

    /**
     * Returns the self-assessment questions matched to the student's target career.
     */
    @GetMapping("/assessment/questions")
    @Operation(
            summary = "Get the career self-assessment questions",
            description = "Returns the skills the authenticated student's target career requires, "
                    + "as a question set. Empty when the career has no skill data yet."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Question set returned",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AssessmentQuestionSetResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "No target career selected yet"),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid access token")
    })
    public ResponseEntity<AssessmentQuestionSetResponse> getAssessmentQuestions() {
        log.info("StudentController: Building the self-assessment question set for the authenticated student");
        return ResponseEntity.ok(studentAssessmentService.getQuestionSet());
    }

    /**
     * Grades a completed self-assessment and applies the result to the roadmap.
     */
    @PostMapping("/assessment/submit")
    @Operation(
            summary = "Submit the career self-assessment",
            description = "Grades the answers with AI, records the resulting skill evidence, and marks "
                    + "the roadmap nodes the student has demonstrably covered."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Assessment graded",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = StudentAssessmentResultResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Invalid answers, or no target career selected"),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid access token")
    })
    public ResponseEntity<StudentAssessmentResultResponse> submitAssessment(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "One answer per question that was served",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SubmitAssessmentRequest.class)
                    )
            )
            @RequestBody @Valid SubmitAssessmentRequest request
    ) {
        log.info("StudentController: Grading a submitted self-assessment for the authenticated student");
        return ResponseEntity.ok(studentAssessmentService.submitAssessment(request));
    }

    // ── Graded assessment ───────────────────────────────────────────────────
    // A real paper with right answers, for the careers that have a question bank
    // in resources/assessment. The client asks for a paper first and falls back to
    // the self-report question set above when there is none, so adding a career's
    // paper later is a file rather than a release.

    /**
     * Returns the graded paper for the student's target career, or 204 when that
     * career has no bank yet.
     */
    @GetMapping("/assessment/paper")
    @Operation(
            summary = "Get the graded assessment paper",
            description = "Multiple-choice, written and coding questions scoped to the student's "
                    + "target career. Answer keys and rubrics are never included. Answers with 204 "
                    + "when the career has no paper, in which case the client uses "
                    + "/assessment/questions instead."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Paper returned",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GradedAssessmentPaperResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "204", description = "No graded paper for this career"),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid access token")
    })
    public ResponseEntity<GradedAssessmentPaperResponse> getAssessmentPaper() {
        log.info("StudentController: Serving the graded assessment paper for the authenticated student");
        return gradedAssessmentService.getPaper()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Grades a sat paper and records what it evidences.
     */
    @PostMapping("/assessment/paper/submit")
    @Operation(
            summary = "Submit the graded assessment",
            description = "Grades the multiple choice against an answer key and the written and code "
                    + "answers against the paper's own rubric, then records the resulting skill "
                    + "evidence. The level comes from the paper, not from anything the student "
                    + "claimed about themselves."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Paper graded",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GradedAssessmentResultResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Unknown question, or no target career selected"),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid access token")
    })
    public ResponseEntity<GradedAssessmentResultResponse> submitAssessmentPaper(
            @RequestBody @Valid SubmitGradedAssessmentRequest request
    ) {
        log.info("StudentController: Grading a sat assessment paper for the authenticated student");
        return ResponseEntity.ok(gradedAssessmentService.submit(request));
    }

    /**
     * Returns the student's most recent assessment, or 204 if they never took one.
     */
    @GetMapping("/assessment/latest")
    @Operation(
            summary = "Get the latest self-assessment result",
            description = "Returns the most recent completed run, or 204 No Content when the student "
                    + "has not taken the assessment."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Latest result returned"),
            @ApiResponse(responseCode = "204", description = "The student has not taken the assessment"),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid access token")
    })
    public ResponseEntity<StudentAssessmentResultResponse> getLatestAssessment() {
        log.info("StudentController: Fetching the latest self-assessment for the authenticated student");
        return studentAssessmentService.getLatestResult()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Returns the student's current career level, or 204 if they have none.
     */
    @GetMapping("/level")
    @Operation(
            summary = "Get the student's career level",
            description = "The level the roadmap, Market Pulse and the AI mentor adapt to. "
                    + "204 No Content means the student has no level, which is different from FRESHER."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Level returned",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = StudentLevelResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "204", description = "The student has no assessed level"),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid access token")
    })
    public ResponseEntity<StudentLevelResponse> getStudentLevel() {
        log.info("StudentController: Resolving the career level for the authenticated student");
        return studentLevelService.currentLevel()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Careers ranked by how much their essential skills overlap the student's.
     *
     * <p>Read-only and advisory. It does not change the student's target career;
     * switching remains an explicit action they take.
     */
    @GetMapping("/career-affinity")
    @Operation(
            summary = "Suggest careers that fit the student's skills",
            description = "Jaccard distance between the student's declared skills and each career's "
                    + "essential skill set, nearest first. A suggestion only — this endpoint never "
                    + "changes the student's target career."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Careers ranked nearest first; empty when no career has skill data",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CareerAffinityResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid access token")
    })
    public ResponseEntity<List<CareerAffinityResponse>> getCareerAffinity(
            @RequestParam(name = "limit", required = false) Integer limit) {
        log.info("StudentController: Ranking careers by skill affinity for the authenticated student");
        return ResponseEntity.ok(careerAffinityService.rankForCurrentStudent(limit));
    }
}
