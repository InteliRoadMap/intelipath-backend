package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class SetupStudentProfileRequest {

    @Size(max = 200, message = "University name must not exceed 200 characters")
    private String universityName;

    /** ISO date, e.g. 2023-09-05. */
    @PastOrPresent(message = "Admission date cannot be in the future")
    private LocalDate admissionDate;

    @Size(max = 200, message = "Major must not exceed 200 characters")
    private String major;

    private UUID careerId;

    @Size(max = 255, message = "GitHub profile must not exceed 255 characters")
    private String githubProfile;

    @Size(max = 2000, message = "Bio must not exceed 2000 characters")
    private String bio;

    @Size(max = 10, message = "Year of birth must not exceed 10 characters")
    private String yob;
}
