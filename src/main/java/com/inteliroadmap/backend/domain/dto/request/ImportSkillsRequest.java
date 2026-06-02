package com.inteliroadmap.backend.domain.dto.request;

import com.inteliroadmap.backend.domain.entity.Skill;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ImportSkillsRequest {

    @NotNull(message = "Student ID is required")
    private UUID studentId;

    @NotEmpty(message = "Selected skills is required")
    private List<Skill> skillList;
}
