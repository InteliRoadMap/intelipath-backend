package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Body of the GitHub sync link callback: the one-time authorization {@code code} GitHub
 * returned to the frontend, which the server exchanges for an access token.
 */
@Data
public class GithubLinkRequest {

    @NotBlank(message = "Missing GitHub authorization code")
    private String code;
}
