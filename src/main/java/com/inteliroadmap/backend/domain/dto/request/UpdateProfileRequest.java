package com.inteliroadmap.backend.domain.dto.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateProfileRequest {
    private String fullName;
    private LocalDate yob;
    private String bio;
    private String university;
    private String department;
}
