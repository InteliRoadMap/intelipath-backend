package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.SelectAlternativeRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateNodeProgressRequest;
import com.inteliroadmap.backend.domain.dto.response.roadmap.ChoiceOptionsResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.NodeSelectionResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapNodeResponse;
import com.inteliroadmap.backend.domain.dto.response.plan.LearningPlanResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.StudentRoadmapResponse;
import com.inteliroadmap.backend.services.RoadmapSelectionService;
import com.inteliroadmap.backend.services.RoadmapService;
import com.inteliroadmap.backend.services.RoadmapPersonalizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.HashSet;
import java.util.Set;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller responsible for handling roadmap and learning progress API endpoints.
 * Provides functionality to fetch career roadmaps, view specific learning nodes, and track user progress.
 */
@RestController
@RequestMapping("/api/v1/roadmaps")
@RequiredArgsConstructor
@Tag(name = "Roadmap", description = "Dynamic Roadmap APIs for Students")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('STUDENT')")
public class RoadmapController {

    private final RoadmapService roadmapService;
    private final RoadmapSelectionService roadmapSelectionService;
    private final RoadmapPersonalizationService roadmapPersonalizationService;

    @GetMapping("/student")
    @Operation(summary = "Get student's roadmap", description = "Fetch the entire roadmap (Flat Array) of the logged-in student, combined with progress status to render the Map.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentRoadmapResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Not logged in or invalid token"),
            @ApiResponse(responseCode = "404", description = "Not Found - Student not found or student does not have a Career")
    })
    public ResponseEntity<StudentRoadmapResponse> getStudentRoadmap(
            @RequestParam(name = "expand", required = false) List<UUID> expand) {
        // Repairs parent-topic state left by older evidence imports before serving
        // the graph. The reconciliation is idempotent and runs in its own transaction.
        roadmapPersonalizationService.reconcileCompletedTopicsForCurrentStudent();
        // ?expand=<nodeId>&expand=<nodeId> opens those topics all the way down.
        // Absent means the default slice, so every existing caller is unaffected.
        return ResponseEntity.ok(roadmapService.getStudentRoadmap(
                expand == null ? Set.of() : new HashSet<>(expand)));
    }

    @GetMapping("/student/sub/{nodeId}")
    @Operation(summary = "Open one standalone roadmap under the career",
            description = "Languages, frameworks and database tracks are whole roadmaps in their "
                    + "own right. This serves that subtree, with a breadcrumb back out. Sliced to "
                    + "the same depth as the career roadmap and opened the same way, via "
                    + "?expand=<nodeId>.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentRoadmapResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Not logged in or invalid token"),
            @ApiResponse(responseCode = "404", description = "Not Found - No such roadmap node")
    })
    public ResponseEntity<StudentRoadmapResponse> getStudentSubRoadmap(
            @PathVariable UUID nodeId,
            @RequestParam(name = "expand", required = false) List<UUID> expand) {
        roadmapPersonalizationService.reconcileCompletedTopicsForCurrentStudent();
        return ResponseEntity.ok(roadmapService.getStudentSubRoadmap(
                nodeId, expand == null ? Set.of() : new HashSet<>(expand)));
    }

    @GetMapping("/student/plan")
    @Operation(summary = "Get the student's personalised learning plan",
            description = "What to learn next, derived from the student's own evidence and live "
                    + "market demand rather than from the career's node catalog. Each step carries "
                    + "the sentence that justifies it.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LearningPlanResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Not logged in or invalid token")
    })
    public ResponseEntity<LearningPlanResponse> getStudentPlan() {
        return ResponseEntity.ok(roadmapService.getStudentPlan());
    }

    @GetMapping("/{careerId}")
    @Operation(summary = "Get career roadmap template", description = "Fetch the base roadmap template of a specific Career (without student progress).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentRoadmapResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Not Found - Career not found")
    })
    public ResponseEntity<StudentRoadmapResponse> getCareerRoadmapTemplate(
            @PathVariable UUID careerId) {
        return ResponseEntity.ok(roadmapService.getCareerRoadmapTemplate(careerId));
    }

    @GetMapping("/{careerId}/progress")
    @Operation(summary = "Get career progress", description = "Fetch the overall progress percentage of a student for a Career.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(type = "integer"))),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Integer> getCareerProgress(
            @PathVariable UUID careerId) {
        return ResponseEntity.ok(roadmapService.getCareerProgress(careerId));
    }

    @GetMapping("/nodes/{nodeId}")
    @Operation(summary = "Get node detail", description = "Fetch details of a single Node in the roadmap.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoadmapNodeResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Not Found - Node not found")
    })
    public ResponseEntity<RoadmapNodeResponse> getNodeDetail(
            @PathVariable UUID nodeId) {
        return ResponseEntity.ok(roadmapService.getNodeDetail(nodeId));
    }

    @PutMapping("/nodes/progress")
    @Operation(summary = "Update node progress", description = "Update the status of a Node (e.g., 'completed', 'in_progress').")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful"),
            @ApiResponse(responseCode = "400", description = "Bad Request - Invalid payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Not Found - Node or Student not found")
    })
    public ResponseEntity<Void> updateNodeProgress(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Update Node progress request payload",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateNodeProgressRequest.class)
                    )
            )
            @Valid @RequestBody UpdateNodeProgressRequest request) {
        roadmapService.updateNodeProgress(request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/selections")
    @Operation(summary = "Select an alternative in a choose-one group",
            description = "Pick (or switch to) one alternative inside a CHOOSE_ONE roadmap group, "
                    + "e.g. choose 'Java' within 'Pick a Language'. Re-sending with a different "
                    + "chosen node switches the stored choice.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = NodeSelectionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Not a choose-one group, node outside the group, or no career selected"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Not Found - Group or chosen node not found")
    })
    public ResponseEntity<NodeSelectionResponse> selectAlternative(
            @Valid @RequestBody SelectAlternativeRequest request) {
        return ResponseEntity.ok(roadmapSelectionService.selectAlternative(request));
    }

    @GetMapping("/selections")
    @Operation(summary = "Get stored choose-one selections",
            description = "All alternatives the logged-in student has picked, one per CHOOSE_ONE group.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = NodeSelectionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<NodeSelectionResponse>> getSelections() {
        return ResponseEntity.ok(roadmapSelectionService.getSelections());
    }

    @GetMapping("/selections/{groupNodeId}/options")
    @Operation(summary = "Rank the options of one choose-one group",
            description = "Every alternative of the group, ranked by how well it fits the student's "
                    + "skills, with the market figures beside each. Read-only: opening the chooser "
                    + "never stores a selection. `verdict` is DECISIVE, TOO_CLOSE or NO_SIGNAL — the "
                    + "last two mean no option may be presented as recommended.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChoiceOptionsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Not a CHOOSE_ONE group"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Not Found - Group does not exist in this career")
    })
    public ResponseEntity<ChoiceOptionsResponse> getChoiceOptions(@PathVariable UUID groupNodeId) {
        return ResponseEntity.ok(roadmapSelectionService.getOptions(groupNodeId));
    }

    @DeleteMapping("/selections/{groupNodeId}")
    @Operation(summary = "Clear a choose-one selection",
            description = "Remove the stored choice for one CHOOSE_ONE group, returning it to 'not chosen yet'.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Selection removed"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Not Found - No selection stored for this group")
    })
    public ResponseEntity<Void> clearSelection(@PathVariable UUID groupNodeId) {
        roadmapSelectionService.clearSelection(groupNodeId);
        return ResponseEntity.noContent().build();
    }
}
