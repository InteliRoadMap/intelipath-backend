package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.SaveNodePositionsRequest;
import com.inteliroadmap.backend.domain.dto.request.UpsertRoadmapNodeRequest;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapNodeResponse;
import com.inteliroadmap.backend.services.RoadmapEditorService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller - Roadmap Editor API Endpoints (mentor authoring)
 * Provides endpoints:
 * - GET    /api/v1/roadmaps/editor/careers/{careerId}/nodes - Full node list for editing
 * - PUT    /api/v1/roadmaps/editor/nodes/positions - Bulk save dragged positions
 * - POST   /api/v1/roadmaps/editor/careers/{careerId}/nodes - Create a node
 * - PUT    /api/v1/roadmaps/editor/nodes/{nodeId} - Update a node
 * - DELETE /api/v1/roadmaps/editor/nodes/{nodeId} - Delete a node
 */
@RestController
@RequestMapping("/api/v1/roadmaps/editor")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('MENTOR')")
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Roadmap Editor", description = "Mentor authoring of career roadmap templates")
public class RoadmapEditorController {

    private final RoadmapEditorService roadmapEditorService;

    @GetMapping("/careers/{careerId}/nodes")
    @Operation(
            summary = "List roadmap nodes for editing",
            description = "Returns every node of the career with full authoring metadata "
                    + "(stage, completion policy, weight, references, resources, positions)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Nodes retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = RoadmapNodeResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Caller is not a mentor"),
            @ApiResponse(responseCode = "404", description = "Career not found")
    })
    public ResponseEntity<List<RoadmapNodeResponse>> getCareerNodes(@PathVariable UUID careerId) {
        log.info("RoadmapEditorController: Editor node list request received. careerId: {}", careerId);
        return ResponseEntity.ok(roadmapEditorService.getCareerNodes(careerId));
    }

    @PutMapping("/nodes/positions")
    @Operation(
            summary = "Save node positions",
            description = "Bulk-saves hand-placed canvas coordinates after drag-and-drop in the editor"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Positions saved"),
            @ApiResponse(responseCode = "400", description = "Invalid payload"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Caller is not a mentor"),
            @ApiResponse(responseCode = "404", description = "Node not found")
    })
    public ResponseEntity<Void> saveNodePositions(@Valid @RequestBody SaveNodePositionsRequest request) {
        log.info("RoadmapEditorController: Save positions request received. count: {}", request.getPositions().size());
        roadmapEditorService.saveNodePositions(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/careers/{careerId}/nodes")
    @Operation(
            summary = "Create a roadmap node",
            description = "Adds a node to the career roadmap template with optional parent/previous "
                    + "references (validated against cycles and cross-career links) and resource links"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Node created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = RoadmapNodeResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid stage, policy, or reference"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Caller is not a mentor"),
            @ApiResponse(responseCode = "404", description = "Career or referenced node not found")
    })
    public ResponseEntity<RoadmapNodeResponse> createNode(
            @PathVariable UUID careerId,
            @Valid @RequestBody UpsertRoadmapNodeRequest request
    ) {
        log.info("RoadmapEditorController: Create node request received. careerId: {}", careerId);
        return ResponseEntity.ok(roadmapEditorService.createNode(careerId, request));
    }

    @PutMapping("/nodes/{nodeId}")
    @Operation(
            summary = "Update a roadmap node",
            description = "Updates the node's content, stage, policy, weight, references, resources and position"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Node updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = RoadmapNodeResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid stage, policy, or reference"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Caller is not a mentor"),
            @ApiResponse(responseCode = "404", description = "Node not found")
    })
    public ResponseEntity<RoadmapNodeResponse> updateNode(
            @PathVariable UUID nodeId,
            @Valid @RequestBody UpsertRoadmapNodeRequest request
    ) {
        log.info("RoadmapEditorController: Update node request received. nodeId: {}", nodeId);
        return ResponseEntity.ok(roadmapEditorService.updateNode(nodeId, request));
    }

    @DeleteMapping("/nodes/{nodeId}")
    @Operation(
            summary = "Delete a roadmap node",
            description = "Deletes a node. Refused with 409 while students have progress on it or "
                    + "while other nodes still reference it as parent/previous"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Node deleted"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Caller is not a mentor"),
            @ApiResponse(responseCode = "404", description = "Node not found"),
            @ApiResponse(responseCode = "409", description = "Node has student progress or inbound references")
    })
    public ResponseEntity<Void> deleteNode(@PathVariable UUID nodeId) {
        log.info("RoadmapEditorController: Delete node request received. nodeId: {}", nodeId);
        roadmapEditorService.deleteNode(nodeId);
        return ResponseEntity.ok().build();
    }
}
