package com.inteliroadmap.backend.domain.dto.response.mentor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentorStudentResponse {
    private String id;
    private String portfolioSlug;
    private String fullName;
    private String email;
    private String career;
    private String university;
}
