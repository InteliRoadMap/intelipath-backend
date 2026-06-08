package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ImportSkillsRequest {

    @NotEmpty(message = "Selected skills is required")
    private List<@NotNull(message = "Skill ID is required") UUID> skillIds;
}
