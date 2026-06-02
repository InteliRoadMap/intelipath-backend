package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
public class SetupStudentProfileRequest {

    @NotBlank(message = "Token is required")
    private String token;

    private String university;

    private String year_of_admission;

    @NotBlank(message = "Major is required")
    private String major;
}
