package com.inteliroadmap.backend.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentResponse {
    private UUID id;
    private String fullName;
    private String email;
    private LocalDate yob;
    private String bio;
    private String university;
    private LocalDate yearOfAdmission;
    private String major;
    private String githubProfile;
    private String role;
    private UUID careerId;
}
