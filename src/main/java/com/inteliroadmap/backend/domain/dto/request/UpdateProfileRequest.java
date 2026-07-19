package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateProfileRequest {
    @Size(max = 150, message = "Full name must not exceed 150 characters")
    private String fullName;

    private LocalDate admissionDate;

    private LocalDate yob;

    @Size(max = 2000, message = "Bio must not exceed 2000 characters")
    private String bio;

    @Size(max = 200, message = "Department must not exceed 200 characters")
    private String department;
}
