package com.inteliroadmap.backend.domain.dto.response.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * State of the student's GitHub sync link: which account is connected and the scopes granted.
 * {@code repoAccess} tells the UI whether private repositories will be visible.
 *
 * <p>Shared by the link, status and unlink endpoints so the UI reads one shape everywhere:
 * after unlinking, {@code linked} is false and the remaining fields are null.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GithubLinkResponse {
    private boolean linked;
    private String githubLogin;
    private String scopes;
    private boolean repoAccess;
}
