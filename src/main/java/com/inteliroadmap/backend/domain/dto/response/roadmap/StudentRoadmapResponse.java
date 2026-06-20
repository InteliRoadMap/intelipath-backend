package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentRoadmapResponse {
    private String targetCareerRole;
    private Integer progress;
    private List<RoadmapNodeDto> nodes;
}
