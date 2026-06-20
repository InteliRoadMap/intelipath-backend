package com.inteliroadmap.backend.domain.dto.response;

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
    private String prerequisite;
    private String description;

    @Builder.Default
    private List<SkillNode> skillNodes = List.of();
}
