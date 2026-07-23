package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.portfolio.GithubLinkResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.GithubLinkStartResponse;

/**
 * Dedicated GitHub account-linking flow for the portfolio "Sync GitHub" feature.
 *
 * <p>Deliberately separate from OAuth <em>login</em>: linking attaches a GitHub access token
 * to the <em>currently authenticated</em> student (resolved from their JWT), never by matching
 * the GitHub email to a user. That distinction matters when one GitHub account carries several
 * verified emails that map to different InteliPath accounts — login would jump to whichever
 * account owns the primary email, whereas linking always binds the token to the signed-in user.
 */
public interface GithubAccountLinkService {

    /** Builds the GitHub authorize URL (with the invasive {@code repo} scope) and a CSRF state. */
    GithubLinkStartResponse start();

    /**
     * Exchanges the authorization {@code code} for an access token and stores it (encrypted)
     * on the authenticated student's own record.
     */
    GithubLinkResponse complete(String code);

    /** Which GitHub account the authenticated student currently has connected, if any. */
    GithubLinkResponse status();

    /**
     * Disconnects the student's GitHub account: revokes the authorization at GitHub and clears
     * the stored token. Projects already imported into the portfolio are left untouched — they
     * are the student's own content and no longer depend on the link.
     *
     * <p>Safe to call when nothing is linked. To switch accounts, unlink and start again.
     */
    GithubLinkResponse unlink();
}
