package com.inteliroadmap.backend.domain.dto.response.student;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardRoadmapProgressResponse {
    @Builder.Default
    private List<RoadmapStepResponse> steps = List.of();

    private String aiTip;
}
