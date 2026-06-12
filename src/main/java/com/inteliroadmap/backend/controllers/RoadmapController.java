package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.UpdateUserProgressRequest;
import com.inteliroadmap.backend.domain.dto.response.RoadmapResponse;
import com.inteliroadmap.backend.domain.dto.request.UpdateNodeProgressRequest;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapNodeDto;
import com.inteliroadmap.backend.domain.dto.response.roadmap.SkillGapResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.StudentRoadmapResponse;
import com.inteliroadmap.backend.services.RoadmapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
public class RoadmapController {

    private final RoadmapService roadmapService;

    @GetMapping("/student")
    @Operation(summary = "Get student's roadmap", description = "Lấy toàn bộ lộ trình (Flat Array) của sinh viên đang đăng nhập, kết hợp với trạng thái tiến độ để vẽ Map.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentRoadmapResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Chưa đăng nhập hoặc Token sai"),
            @ApiResponse(responseCode = "404", description = "Not Found - Không tìm thấy sinh viên hoặc sinh viên chưa có Career")
    })
    public ResponseEntity<StudentRoadmapResponse> getStudentRoadmap(
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(roadmapService.getStudentRoadmap(authHeader));
    }

    @GetMapping("/{careerId}")
    @Operation(summary = "Get career roadmap template", description = "Lấy template lộ trình gốc của một Career cụ thể (không có tiến độ của sinh viên).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StudentRoadmapResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Not Found - Không tìm thấy Career")
    })
    public ResponseEntity<StudentRoadmapResponse> getCareerRoadmapTemplate(
            @PathVariable UUID careerId) {
        return ResponseEntity.ok(roadmapService.getCareerRoadmapTemplate(careerId));
    }

    @GetMapping("/{careerId}/progress")
    @Operation(summary = "Get career progress", description = "Lấy % tiến độ tổng quan của sinh viên đối với một Career.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(mediaType = "application/json", schema = @Schema(type = "integer"))),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Integer> getCareerProgress(
            @PathVariable UUID careerId,
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(roadmapService.getCareerProgress(careerId, authHeader));
    }

    @GetMapping("/nodes/{nodeId}")
    @Operation(summary = "Get node detail", description = "Lấy chi tiết 1 Node trong lộ trình.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoadmapNodeDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Not Found - Không tìm thấy Node")
    })
    public ResponseEntity<RoadmapNodeDto> getNodeDetail(
            @PathVariable UUID nodeId) {
        return ResponseEntity.ok(roadmapService.getNodeDetail(nodeId));
    }

    @PatchMapping("/nodes/progress")
    @Operation(summary = "Update node progress", description = "Cập nhật trạng thái của 1 Node (vd: 'completed', 'in_progress').")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "400", description = "Bad Request - Payload không hợp lệ"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Not Found - Không tìm thấy Node hoặc Sinh viên")
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
            @Valid @RequestBody UpdateNodeProgressRequest request,
            @RequestHeader("Authorization") String authHeader) {
        roadmapService.updateNodeProgress(request, authHeader);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/skills/compare")
    @Operation(summary = "Compare skills", description = "So sánh kỹ năng hiện tại của sinh viên với các kỹ năng yêu cầu của Career để tìm ra Skill Gaps.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SkillGapResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Not Found - Sinh viên chưa chọn Career")
    })
    public ResponseEntity<SkillGapResponse> compareSkills(
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(roadmapService.compareSkills(authHeader));
    }
}
