package com.inteliroadmap.backend.domain.dto.response.student;

import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillGapItemResponse {
    private UUID id;
    private String type;
    private String title;
    private String description;
    private ImportanceLevel severity;
    private Integer progress;
}
