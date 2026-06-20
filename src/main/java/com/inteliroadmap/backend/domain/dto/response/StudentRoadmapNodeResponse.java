package com.inteliroadmap.backend.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentRoadmapNodeResponse {
    private UUID id;
    private String title;
    private String status;
    private String description;
    private Integer level;
    private UUID childNodeOf;

    @Builder.Default
    private List<StudentRoadmapResourceResponse> resources = List.of();

    @Builder.Default
    private List<StudentRoadmapNodeResponse> children = List.of();
}
