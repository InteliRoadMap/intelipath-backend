package com.inteliroadmap.backend.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class GithubApiClient {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${github.token:}")
    private String githubToken;

    public GithubApiClient(ObjectMapper objectMapper, RestTemplate externalApiRestTemplate) {
        this.objectMapper = objectMapper;
        this.restTemplate = externalApiRestTemplate;
    }

    public GithubRepoMetadata getRepoMetadata(String owner, String repo) {
        return getRepoMetadata(owner, repo, null);
    }

    /**
     * Fetches repo metadata, authenticating with the given user access token when present so
     * private repositories are visible (the {@code accessToken} comes from the student's
     * Connect-GitHub link). Falls back to the app-level token / anonymous when null.
     */
    public GithubRepoMetadata getRepoMetadata(String owner, String repo, String accessToken) {
        HttpHeaders headers = authHeaders(accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo;
        ResponseEntity<String> repoResponse;
        
        try {
            repoResponse = restTemplate.exchange(apiUrl, HttpMethod.GET, entity, String.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "GitHub repository not found or is private.");
        } catch (Exception e) {
            log.error("GithubApiClient: Error fetching GitHub repo", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to connect to GitHub API.");
        }

        try {
            JsonNode repoJson = objectMapper.readTree(repoResponse.getBody());
            String description = repoJson.path("description").asText("");
            String defaultBranch = repoJson.path("default_branch").asText("main");
            int stars = repoJson.path("stargazers_count").asInt(0);
            
            String hp = repoJson.path("homepage").asText(null);
            String homepage = (hp != null && !hp.isBlank() && !hp.equals("null")) ? hp : null;
            
            return new GithubRepoMetadata(description, defaultBranch, stars, homepage);
        } catch (Exception e) {
            log.error("GithubApiClient: Error parsing GitHub repo metadata", e);
            return new GithubRepoMetadata("", "main", 0, null);
        }
    }

    public String fetchFileContent(String rawUrl, int maxLength) {
        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(rawUrl, HttpMethod.GET, entity, String.class);
            String content = response.getBody();
            if (content != null && content.length() > maxLength) {
                return content.substring(0, maxLength);
            }
            return content != null ? content : "";
        } catch (Exception e) {
            return ""; // File not found or error
        }
    }

    /**
     * Lists every repository the authenticated user owns, using their own OAuth access token
     * (so private repos are included). Pages through the GitHub API until exhausted.
     *
     * @param accessToken the user's decrypted GitHub OAuth access token
     * @return all owned repositories; empty list on error (never null)
     */
    public List<GithubRepoSummary> listOwnedRepos(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No GitHub access token available. Please sign in with GitHub again.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Accept", "application/vnd.github+json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        List<GithubRepoSummary> repos = new ArrayList<>();
        int page = 1;
        final int perPage = 100;
        final int maxPages = 10; // Hard cap (1000 repos) to bound work on unusually large accounts.

        while (page <= maxPages) {
            String url = "https://api.github.com/user/repos"
                    + "?per_page=" + perPage
                    + "&page=" + page
                    + "&affiliation=owner"
                    + "&sort=pushed";
            ResponseEntity<String> response;
            try {
                response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "GitHub access was rejected. Please reconnect your GitHub account.");
            } catch (Exception e) {
                log.error("GithubApiClient: Error listing user repos (page {})", page, e);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch repositories from GitHub.");
            }

            List<GithubRepoSummary> pageRepos = parseRepoPage(response.getBody());
            repos.addAll(pageRepos);
            // A short page means we've reached the end; stop paging.
            if (pageRepos.size() < perPage) {
                break;
            }
            page++;
        }

        log.info("GithubApiClient: Listed {} owned repositories", repos.size());
        return repos;
    }

    private List<GithubRepoSummary> parseRepoPage(String body) {
        List<GithubRepoSummary> result = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return result;
        }
        try {
            JsonNode array = objectMapper.readTree(body);
            if (!array.isArray()) {
                return result;
            }
            for (JsonNode repo : array) {
                String pushedAtRaw = repo.path("pushed_at").asText(null);
                OffsetDateTime pushedAt = null;
                if (pushedAtRaw != null && !pushedAtRaw.isBlank() && !"null".equals(pushedAtRaw)) {
                    try {
                        pushedAt = OffsetDateTime.parse(pushedAtRaw);
                    } catch (Exception ignored) {
                        // Leave null; ranking treats a missing timestamp as "old".
                    }
                }
                result.add(new GithubRepoSummary(
                        repo.path("name").asText(""),
                        repo.path("full_name").asText(""),
                        repo.path("html_url").asText(""),
                        emptyToNull(repo.path("description").asText(null)),
                        emptyToNull(repo.path("homepage").asText(null)),
                        emptyToNull(repo.path("language").asText(null)),
                        repo.path("stargazers_count").asInt(0),
                        repo.path("forks_count").asInt(0),
                        repo.path("fork").asBoolean(false),
                        repo.path("private").asBoolean(false),
                        repo.path("archived").asBoolean(false),
                        pushedAt
                ));
            }
        } catch (Exception e) {
            log.error("GithubApiClient: Error parsing repo page", e);
        }
        return result;
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isBlank() || "null".equals(value)) ? null : value;
    }

    /**
     * Reads a single file from a repo through the authenticated Contents API, so it works for
     * private repositories too (unlike raw.githubusercontent.com). Returns "" on any error/miss.
     *
     * @param path repo-relative file path, e.g. {@code README.md}
     */
    public String fetchRepoFile(String owner, String repo, String path, int maxLength, String accessToken) {
        HttpHeaders headers = authHeaders(accessToken);
        // The 'raw' media type returns the file bytes directly instead of the JSON envelope.
        headers.set("Accept", "application/vnd.github.raw");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String url = "https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + path;
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String content = response.getBody();
            if (content != null && content.length() > maxLength) {
                return content.substring(0, maxLength);
            }
            return content != null ? content : "";
        } catch (Exception e) {
            return ""; // File not found (e.g. no README) or unreadable — non-fatal.
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (githubToken != null && !githubToken.isBlank()) {
            headers.setBearerAuth(githubToken);
        }
        headers.set("Accept", "application/vnd.github.v3+json");
        return headers;
    }

    /**
     * Headers authenticated with the user's own access token when supplied (needed for private
     * repos); otherwise falls back to the app-level token / anonymous via {@link #createHeaders()}.
     */
    private HttpHeaders authHeaders(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return createHeaders();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Accept", "application/vnd.github+json");
        return headers;
    }

    public record GithubRepoMetadata(String description, String defaultBranch, int stars, String homepage) {}

    /** Lightweight repo summary used for the Sync-GitHub listing and quality ranking. */
    public record GithubRepoSummary(
            String name,
            String fullName,
            String htmlUrl,
            String description,
            String homepage,
            String language,
            int stars,
            int forks,
            boolean fork,
            boolean isPrivate,
            boolean archived,
            OffsetDateTime pushedAt
    ) {}
}
