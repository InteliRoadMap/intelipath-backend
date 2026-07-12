package com.inteliroadmap.backend.domain.dto.response.roadmap;

import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
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
    private ImportanceLevel importanceLevel;
    private Integer progress;
}
