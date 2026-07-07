package com.inteliroadmap.backend.domain.dto.response.roadmap;

import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.SkillNode;
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
public class CareerResponse {
    private UUID careerId;
    private String careerName;
    private List<CareerRole> prerequisite;
    private String description;

    @Builder.Default
    private List<SkillNode> skillNodes = List.of();
}
