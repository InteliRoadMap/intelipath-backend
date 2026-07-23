package com.inteliroadmap.backend.domain.dto.response.student;

import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapStepResponse {
    private UUID id;
    private String title;
    private RoadmapStepStatus status;
}
