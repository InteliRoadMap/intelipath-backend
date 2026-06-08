package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GetSkillGapRequest {
    @NotBlank(message = "Career name is required")
    private String careerName;
}
