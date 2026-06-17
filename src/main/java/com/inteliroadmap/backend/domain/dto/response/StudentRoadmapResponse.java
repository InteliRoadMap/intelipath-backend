package com.inteliroadmap.backend.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentRoadmapResponse {
    private String targetCareerRole;
    private int progress;

    @Builder.Default
    private List<StudentRoadmapNodeResponse> nodes = List.of();
}
