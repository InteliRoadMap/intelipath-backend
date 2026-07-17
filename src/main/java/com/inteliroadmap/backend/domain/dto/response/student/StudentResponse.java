package com.inteliroadmap.backend.domain.dto.response.student;

import com.inteliroadmap.backend.domain.enums.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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
    private String universityName;
    /** Read-only: set from the User record, never accepted from a request. */
    private AccountType accountType;
    private LocalDate admissionDate;
    private String major;
    private String githubProfile;
    private String transcriptUrl;
    private String role;
    private UUID careerId;
    private String careerName;
}
