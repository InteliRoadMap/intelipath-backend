package com.inteliroadmap.backend.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SkillResponse {
    @Builder.Default
    private List<SkillItemResponse> selectedSkills = List.of();

    @Builder.Default
    private List<SkillItemResponse> skills = List.of();

    @Builder.Default
    private List<RequiredSkillResponse> requiredSkills = List.of();

    @Builder.Default
    private List<SkillItemResponse> missingSkills = List.of();
}
