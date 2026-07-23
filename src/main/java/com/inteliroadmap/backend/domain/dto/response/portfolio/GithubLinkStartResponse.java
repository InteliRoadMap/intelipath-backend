package com.inteliroadmap.backend.domain.dto.response.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kick-off payload for the "Connect GitHub" sync link flow: the GitHub authorize URL the
 * browser should navigate to, plus a random {@code state} the frontend stores and re-checks
 * on the callback (CSRF protection for the OAuth redirect).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GithubLinkStartResponse {
    private String authorizeUrl;
    private String state;
}
