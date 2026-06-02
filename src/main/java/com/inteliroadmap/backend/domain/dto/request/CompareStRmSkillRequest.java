package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CompareStRmSkillRequest {

    @NotBlank(message = "Student ID is required")
    private UUID studentId;

    @NotBlank(message = "Career ID is required")
    private UUID careerId;
}
