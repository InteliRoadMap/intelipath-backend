package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class SetupStudentProfileRequest {

    private UUID universityId;

    @Size(max = 200, message = "University name must not exceed 200 characters")
    private String universityName;

    @Min(value = 1950, message = "Year of admission is out of range")
    @Max(value = 2100, message = "Year of admission is out of range")
    private Integer yearOfAdmission;

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
