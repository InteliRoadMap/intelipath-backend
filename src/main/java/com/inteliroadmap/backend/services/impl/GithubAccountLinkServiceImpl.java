package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.response.portfolio.GithubLinkResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.GithubLinkStartResponse;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.security.TokenCipher;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.services.GithubAccountLinkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of {@link GithubAccountLinkService}. Uses GitHub's web application flow with
 * the client secret held server-side; the frontend only relays the one-time {@code code}.
 */
@Service
@Slf4j
public class GithubAccountLinkServiceImpl implements GithubAccountLinkService {

    private static final String AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";
    private static final String REVOKE_GRANT_URL = "https://api.github.com/applications/{clientId}/grant";
    // read:user for the profile, repo so the sync picker can see private repositories too.
    //
    // read:org is what makes a team project importable at all. GitHub conceals
    // organisation membership by default, and a token without this scope is told the
    // student belongs to no organisation — `/user/orgs` answered "none" for an account
    // that plainly has one, since it holds a fork of that organisation's repository.
    // Without it, `affiliation=organization_member` returns nothing either, so a
    // student whose main work lives under their team's organisation could import none
    // of it. Read-only: it grants sight of membership and teams, nothing more.
    private static final String SCOPES = "read:user repo read:org";

    private final StudentRepository studentRepository;
    private final AuthenticatedStudentService authenticatedStudentService;
    private final TokenCipher tokenCipher;
    private final RestClient restClient = RestClient.create();

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public GithubAccountLinkServiceImpl(
            StudentRepository studentRepository,
            AuthenticatedStudentService authenticatedStudentService,
            TokenCipher tokenCipher,
            @Value("${github.link-client-id:}") String clientId,
            @Value("${github.link-client-secret:}") String clientSecret,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendBaseUrl) {
        this.studentRepository = studentRepository;
        this.authenticatedStudentService = authenticatedStudentService;
        this.tokenCipher = tokenCipher;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        // Must exactly match one of the GitHub OAuth App's Authorization callback URLs.
        this.redirectUri = stripTrailingSlash(frontendBaseUrl) + "/github/callback";
    }

    @Override
    public GithubLinkStartResponse start() {
        // Ensure only a signed-in student can begin linking (and fail early if sync is off).
        authenticatedStudentService.getRequiredStudent();
        requireConfigured();

        String state = UUID.randomUUID().toString();
        String authorizeUrl = UriComponentsBuilder.fromHttpUrl(AUTHORIZE_URL)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", SCOPES)
                .queryParam("state", state)
                .queryParam("allow_signup", "false")
                .build()
                .encode()
                .toUriString();

        return GithubLinkStartResponse.builder().authorizeUrl(authorizeUrl).state(state).build();
    }

    @Override
    @Transactional
    public GithubLinkResponse complete(String code) {
        Student student = authenticatedStudentService.getRequiredStudent();
        requireConfigured();

        // 1. Exchange the one-time code for an access token.
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("redirect_uri", redirectUri);

        Map<?, ?> tokenResponse;
        try {
            tokenResponse = restClient.post()
                    .uri(TOKEN_URL)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.error("GithubAccountLinkServiceImpl: token exchange call failed", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach GitHub to complete linking.");
        }

        if (tokenResponse == null || tokenResponse.get("access_token") == null) {
            String error = tokenResponse == null ? "empty response" : String.valueOf(tokenResponse.get("error"));
            log.warn("GithubAccountLinkServiceImpl: token exchange returned no access token: {}", error);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "GitHub authorization failed or expired. Please try connecting again.");
        }

        String accessToken = String.valueOf(tokenResponse.get("access_token"));
        String grantedScopes = tokenResponse.get("scope") != null ? String.valueOf(tokenResponse.get("scope")) : "";

        // 2. Resolve the GitHub identity so the UI can show who is connected.
        GithubIdentity identity = fetchGithubIdentity(accessToken);
        String githubLogin = identity.login();

        // 3. Persist the token (encrypted) on the authenticated student — not on any oauth_account.
        student.setGithubSyncTokenEnc(tokenCipher.encrypt(accessToken));
        student.setGithubSyncScopes(grantedScopes);
        student.setGithubLogin(githubLogin);
        // Connecting an account is the most explicit "this is my GitHub" the product ever
        // gets, so it fills the profile link too. Without this the student's github_profile
        // stayed empty even though the app knew exactly which account they had authorised.
        String profileUrl = identity.profileUrl();
        if (profileUrl != null && !profileUrl.isBlank()) {
            student.setGithubProfile(profileUrl);
        }
        studentRepository.save(student);

        boolean repoAccess = grantedScopes.contains("repo");
        log.info("GithubAccountLinkServiceImpl: linked GitHub '{}' to student {} (repoAccess={})",
                githubLogin, student.getUserId(), repoAccess);

        return GithubLinkResponse.builder()
                .linked(true)
                .githubLogin(githubLogin)
                .scopes(grantedScopes)
                .repoAccess(repoAccess)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public GithubLinkResponse status() {
        Student student = authenticatedStudentService.getRequiredStudent();
        return describeLink(student);
    }

    @Override
    @Transactional
    public GithubLinkResponse unlink() {
        Student student = authenticatedStudentService.getRequiredStudent();

        if (student.getGithubSyncTokenEnc() == null) {
            log.info("GithubAccountLinkServiceImpl: unlink requested but student {} has no linked GitHub account",
                    student.getUserId());
            return describeLink(student);
        }

        // Revoke first, but never let GitHub being unreachable trap the student in a linked
        // state they asked to leave — clearing our own copy is what they can actually observe.
        String accessToken = tokenCipher.decrypt(student.getGithubSyncTokenEnc());
        if (accessToken != null) {
            revokeGrant(accessToken);
        }

        String previousLogin = student.getGithubLogin();
        student.setGithubSyncTokenEnc(null);
        student.setGithubSyncScopes(null);
        student.setGithubLogin(null);
        studentRepository.save(student);

        log.info("GithubAccountLinkServiceImpl: unlinked GitHub '{}' from student {}",
                previousLogin, student.getUserId());

        return describeLink(student);
    }

    private GithubLinkResponse describeLink(Student student) {
        String scopes = student.getGithubSyncScopes();
        return GithubLinkResponse.builder()
                .linked(student.getGithubSyncTokenEnc() != null)
                .githubLogin(student.getGithubLogin())
                .scopes(scopes)
                .repoAccess(scopes != null && scopes.contains("repo"))
                .build();
    }

    /**
     * Revokes the whole authorization grant, not just this one token. Revoking the grant also
     * makes GitHub show the consent screen on the next link, which is what a student switching
     * accounts expects to see rather than a silent re-connect to the account they just left.
     */
    private void revokeGrant(String accessToken) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            return;
        }
        String credentials = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        try {
            restClient.method(HttpMethod.DELETE)
                    .uri(REVOKE_GRANT_URL, clientId)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("access_token", accessToken))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // Best effort: the token is already being dropped on our side either way.
            log.warn("GithubAccountLinkServiceImpl: could not revoke GitHub grant: {}", e.getMessage());
        }
    }

    /** Who authorised the link: the username for display and the profile URL for the record. */
    private record GithubIdentity(String login, String profileUrl) {}

    private GithubIdentity fetchGithubIdentity(String accessToken) {
        try {
            Map<?, ?> user = restClient.get()
                    .uri(USER_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .body(Map.class);
            if (user == null) {
                return new GithubIdentity(null, null);
            }
            String login = user.get("login") != null ? String.valueOf(user.get("login")) : null;
            // html_url comes back on the same call; deriving it from the login instead would
            // guess at a URL GitHub already told us.
            String profileUrl = user.get("html_url") != null ? String.valueOf(user.get("html_url")) : null;
            return new GithubIdentity(login, profileUrl);
        } catch (Exception e) {
            // Non-fatal: the token is what matters for sync; the identity is for display.
            log.warn("GithubAccountLinkServiceImpl: could not fetch GitHub identity: {}", e.getMessage());
            return new GithubIdentity(null, null);
        }
    }

    private void requireConfigured() {
        if (!tokenCipher.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GitHub sync is not configured on this server.");
        }
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GitHub OAuth is not configured on this server.");
        }
    }

    private static String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
