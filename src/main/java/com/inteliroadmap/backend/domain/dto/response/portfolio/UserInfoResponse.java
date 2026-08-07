package com.inteliroadmap.backend.domain.dto.response.portfolio;

import com.inteliroadmap.backend.domain.enums.EvidenceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserInfoResponse {
    // The slug addresses the portfolio, but feedback is addressed to a person:
    // a viewer who arrived by slug still needs the id to write back.
    private UUID userId;
    private String fullName;
    private String bio;
    private String email;
    private String portfolioSlug;
    private String university;
    private String avatarUrl;
}
