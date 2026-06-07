package com.inteliroadmap.backend.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequiredSkillResponse {
    private SkillItemResponse skill;
    private String importanceLevel;
}
