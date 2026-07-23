package com.inteliroadmap.backend.domain.dto.response.mentor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One mentor as a student sees them when choosing who to ask for a portfolio review.
 *
 * The email is here on purpose: it is the identifier POST /portfolio/request-review
 * takes, and before this endpoint existed a student had no way to learn it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorDirectoryResponse {

    private String userId;
    private String fullName;
    private String email;
    private String avatarUrl;
    private String company;
    private String industryFocus;
}
