package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadmapNodeDto {
    private UUID nodeId;
    private String nodeName;
    private java.util.List<UUID> prerequisite;
    private String parentNode;
    private Integer nodeLevel;
    private String description;
    private Object resource;
    private String status;
}
