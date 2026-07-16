package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.Builder;
import lombok.Data;

/** One selectable FLM curriculum version, for the student's curriculum picker. */
@Data
@Builder
public class CurriculumOptionResponse {

    private String id;
    private String code;
    private String program;
    private Integer cohort;
    private String batch;
    private boolean isDefault;
}
