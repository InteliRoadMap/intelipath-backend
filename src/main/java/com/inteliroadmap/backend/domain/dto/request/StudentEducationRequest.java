package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StudentEducationRequest {
    private UUID educationId;

    @Size(max = 200, message = "University must not exceed 200 characters")
    private String university;

    @Size(max = 200, message = "Degree must not exceed 200 characters")
    private String degree;

    @Size(max = 100, message = "Period must not exceed 100 characters")
    private String period;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;
}
