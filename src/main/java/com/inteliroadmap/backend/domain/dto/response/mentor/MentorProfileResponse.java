package com.inteliroadmap.backend.domain.dto.response.mentor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorProfileResponse {
    private UUID userId;
    private String email;
    private String fullName;
    private LocalDate yob;
    private String bio;
    private String avatar;
    private String role;
    private String company;
    private String industryFocus;
}
