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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
                    // owner alone hid every repository a student contributes to under an
                    // organisation — a team project could not be imported at all, which is
                    // the commonest shape a university project takes. Collaborator repos
                    // come along for the same reason. The picker ranks and the student
                    // ticks, so breadth here costs nothing that filtering does not fix.
                    + "&affiliation=owner,organization_member,collaborator"
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

        // Organisation repositories again, by a different route.
        //
        // `affiliation=organization_member` alone proved not to be enough: it returned
        // nothing for a student who has a fork of their team's organisation repository,
        // so the organisation is reachable but its repositories were not listed. Asking
        // each organisation directly answers a narrower question and, when it also comes
        // back empty, says plainly that GitHub is withholding them rather than that the
        // parameter was wrong. Names are logged either way so the cause is visible.
        List<String> orgs = listOrganizations(accessToken);
        if (!orgs.isEmpty()) {
            Set<String> seen = repos.stream().map(GithubRepoSummary::fullName).collect(Collectors.toSet());
            for (String org : orgs) {
                for (GithubRepoSummary repo : listOrgRepos(org, accessToken)) {
                    if (seen.add(repo.fullName())) {
                        repos.add(repo);
                    }
                }
            }
        }
        log.info("GithubApiClient: organisations visible to this token: {}", orgs.isEmpty() ? "none" : orgs);
        collapseForksOntoTheirUpstream(repos, accessToken);

        log.info("GithubApiClient: Listed {} repositories ({} fork(s), {} of them worked in).",
                repos.size(),
                repos.stream().filter(GithubRepoSummary::fork).count(),
                repos.stream().filter(GithubRepoSummary::isWorkedInFork).count());
        return repos;
    }

    /**
     * Drops a fork when the repository it was forked from is also in the list.
     *
     * <p>A student who owns the organisation and forks its repository to work in ends
     * up entitled to both, and they are the same project twice: the picker offered
     * `InteliRoadMap/intelipath-backend` and `dpvinh30092005/intelipath-backend` as
     * separate choices, and importing both would file one project as two.
     *
     * <p>The upstream is what survives, not the fork. A portfolio should link to the
     * project rather than to a copy of it, and the upstream carries the full history
     * and the address the student would actually share. The fork's originality bonus
     * is not lost with it: the upstream is not a fork, so it scores that outright.
     *
     * <p>Matching is on the fork's declared parent, never on the name. `parent` is
     * absent from the list payload and needs a request per repository, so it is
     * fetched only for forks whose name collides with something else already listed —
     * normally none, and never more than a handful. Two unrelated repositories that
     * happen to share a name resolve to different parents and both stay.
     */
    private void collapseForksOntoTheirUpstream(List<GithubRepoSummary> repos, String accessToken) {
        Map<String, Long> byName = repos.stream()
                .collect(Collectors.groupingBy(GithubRepoSummary::name, Collectors.counting()));
        List<GithubRepoSummary> contested = repos.stream()
                .filter(GithubRepoSummary::fork)
                .filter(repo -> byName.getOrDefault(repo.name(), 0L) > 1)
                .toList();
        if (contested.isEmpty()) {
            return;
        }

        Set<String> present = repos.stream().map(GithubRepoSummary::fullName).collect(Collectors.toSet());
        Set<String> drop = new HashSet<>();
        for (GithubRepoSummary fork : contested) {
            String parent = fetchParentFullName(fork.fullName(), accessToken);
            if (parent != null && present.contains(parent)) {
                drop.add(fork.fullName());
                log.info("GithubApiClient: '{}' is a fork of '{}', which is also listed; keeping the upstream.",
                        fork.fullName(), parent);
            }
        }
        repos.removeIf(repo -> drop.contains(repo.fullName()));
    }

    /** The `full_name` a fork was forked from, or null when unknown. */
    private String fetchParentFullName(String fullName, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Accept", "application/vnd.github+json");
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.github.com/repos/" + fullName,
                    HttpMethod.GET, new HttpEntity<>(headers), String.class);
            JsonNode parent = objectMapper.readTree(response.getBody()).path("parent").path("full_name");
            return parent.isMissingNode() || parent.asText("").isBlank() ? null : parent.asText();
        } catch (Exception e) {
            log.warn("GithubApiClient: could not read the parent of '{}': {}", fullName, e.getMessage());
            return null;
        }
    }

    /**
     * Organisations this token can see. Empty is a real answer, not an error: the
     * student may belong to none, may have their membership set to private without
     * `read:org`, or the organisation may not have approved this OAuth App under its
     * third-party application access policy. All three end here, so the caller logs
     * the result rather than failing.
     */
    private List<String> listOrganizations(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Accept", "application/vnd.github+json");
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.github.com/user/orgs?per_page=100",
                    HttpMethod.GET, new HttpEntity<>(headers), String.class);
            JsonNode array = objectMapper.readTree(response.getBody());
            if (!array.isArray()) {
                return List.of();
            }
            List<String> names = new ArrayList<>();
            for (JsonNode org : array) {
                String login = org.path("login").asText("");
                if (!login.isBlank()) {
                    names.add(login);
                }
            }
            return names;
        } catch (Exception e) {
            log.warn("GithubApiClient: could not list organisations: {}", e.getMessage());
            return List.of();
        }
    }

    /** One organisation's repositories, or an empty list when it withholds them. */
    private List<GithubRepoSummary> listOrgRepos(String org, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Accept", "application/vnd.github+json");
        List<GithubRepoSummary> repos = new ArrayList<>();
        int page = 1;
        while (page <= 5) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        "https://api.github.com/orgs/" + org + "/repos?per_page=100&sort=pushed&page=" + page,
                        HttpMethod.GET, new HttpEntity<>(headers), String.class);
                List<GithubRepoSummary> pageRepos = parseRepoPage(response.getBody());
                repos.addAll(pageRepos);
                if (pageRepos.size() < 100) {
                    break;
                }
                page++;
            } catch (Exception e) {
                log.warn("GithubApiClient: could not list repositories for organisation '{}': {}", org, e.getMessage());
                break;
            }
        }
        log.info("GithubApiClient: organisation '{}' returned {} repositor(y/ies).", org, repos.size());
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
                OffsetDateTime createdAt = parseTimestamp(repo.path("created_at").asText(null));
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
                        pushedAt,
                        createdAt
                ));
            }
        } catch (Exception e) {
            log.error("GithubApiClient: Error parsing repo page", e);
        }
        return result;
    }

    private static OffsetDateTime parseTimestamp(String raw) {
        if (raw == null || raw.isBlank() || "null".equals(raw)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw);
        } catch (Exception ignored) {
            return null;
        }
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

    /**
     * Who committed to this repository, and how much.
     *
     * <p>Keyed by GitHub <em>login</em>, not by commit e-mail. GitHub has already done the
     * hard half of attribution: it maps a commit's e-mail to the account that verified it,
     * including squash-merges, where the squashed commit keeps the pull request author. A
     * student who commits from a work laptop under a different address is still counted,
     * which an e-mail comparison of our own would have got wrong.
     *
     * @return contributors, or {@code null} when the answer could not be obtained. Null and
     *         empty mean different things here and callers must not conflate them: an empty
     *         list is GitHub saying nobody contributed, null is GitHub not saying anything.
     *         Treating an outage as "this student wrote nothing" would turn a network blip
     *         into an accusation.
     */
    public List<ContributorStat> listContributors(String owner, String repo, String accessToken) {
        String url = "https://api.github.com/repos/" + owner + "/" + repo
                + "/contributors?per_page=100&anon=false";
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(authHeaders(accessToken)), String.class);
            // 202 means GitHub is still computing the statistics and the body is empty. That
            // is "ask again later", not "no contributors".
            if (response.getStatusCode() == HttpStatus.ACCEPTED || response.getBody() == null) {
                log.info("GithubApiClient: contributor list for {}/{} not ready yet (status {}).",
                        owner, repo, response.getStatusCode());
                return null;
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            if (!root.isArray()) {
                return null;
            }
            List<ContributorStat> contributors = new ArrayList<>();
            for (JsonNode node : root) {
                String login = emptyToNull(node.path("login").asText(null));
                if (login != null) {
                    contributors.add(new ContributorStat(login, node.path("contributions").asInt(0)));
                }
            }
            return contributors;
        } catch (Exception e) {
            log.warn("GithubApiClient: could not read contributors for {}/{}: {}", owner, repo, e.getMessage());
            return null;
        }
    }

    /**
     * Bytes of source per language, straight from GitHub's own analysis of the tree.
     *
     * <p>This is the closest thing to reading the code that costs one request. A build file
     * lists what was declared; this counts what was actually written, so a repository with a
     * React dependency and four lines of JSX cannot pass itself off as a frontend project.
     *
     * @return language → bytes, or an empty map when unavailable
     */
    public Map<String, Long> fetchLanguageBytes(String owner, String repo, String accessToken) {
        String url = "https://api.github.com/repos/" + owner + "/" + repo + "/languages";
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(authHeaders(accessToken)), String.class);
            if (response.getBody() == null) {
                return Map.of();
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            Map<String, Long> bytes = new LinkedHashMap<>();
            root.fields().forEachRemaining(entry -> bytes.put(entry.getKey(), entry.getValue().asLong(0)));
            return bytes;
        } catch (Exception e) {
            log.warn("GithubApiClient: could not read languages for {}/{}: {}", owner, repo, e.getMessage());
            return Map.of();
        }
    }

    /**
     * Lists source paths without downloading their contents. The caller selects a
     * very small representative sample, keeping repository analysis evidence-based
     * without sending the whole codebase to the model.
     */
    public List<String> listRepositoryFiles(String owner, String repo, String branch, String accessToken) {
        String url = "https://api.github.com/repos/" + owner + "/" + repo
                + "/git/trees/" + branch + "?recursive=1";
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(authHeaders(accessToken)), String.class);
            if (response.getBody() == null) {
                return List.of();
            }
            JsonNode tree = objectMapper.readTree(response.getBody()).path("tree");
            if (!tree.isArray()) {
                return List.of();
            }
            List<String> paths = new ArrayList<>();
            for (JsonNode entry : tree) {
                if ("blob".equals(entry.path("type").asText()) && entry.hasNonNull("path")) {
                    paths.add(entry.path("path").asText());
                }
            }
            return paths;
        } catch (Exception e) {
            log.warn("GithubApiClient: could not list repository tree for {}/{}: {}",
                    owner, repo, e.getMessage());
            return List.of();
        }
    }

    /**
     * The student's own commit messages, newest first.
     *
     * <p>What a repository <em>is</em> and what one person <em>did in it</em> are different
     * questions, and only the README was ever answering the first. "Add JWT refresh token
     * rotation" is evidence of a skill in a way that a dependency list is not.
     *
     * @param limit how many messages to keep; only the subject line of each is returned,
     *              since commit bodies are mostly issue links and co-author trailers
     */
    public List<String> listCommitMessagesByAuthor(String owner, String repo, String authorLogin,
                                                   int limit, String accessToken) {
        if (authorLogin == null || authorLogin.isBlank()) {
            return List.of();
        }
        String url = "https://api.github.com/repos/" + owner + "/" + repo
                + "/commits?author=" + authorLogin + "&per_page=" + Math.min(limit, 100);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(authHeaders(accessToken)), String.class);
            if (response.getBody() == null) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            if (!root.isArray()) {
                return List.of();
            }
            List<String> messages = new ArrayList<>();
            for (JsonNode node : root) {
                String message = emptyToNull(node.path("commit").path("message").asText(null));
                if (message == null) {
                    continue;
                }
                int newline = message.indexOf('\n');
                messages.add(newline > 0 ? message.substring(0, newline).trim() : message.trim());
                if (messages.size() >= limit) {
                    break;
                }
            }
            return messages;
        } catch (Exception e) {
            log.warn("GithubApiClient: could not read commits by '{}' in {}/{}: {}",
                    authorLogin, owner, repo, e.getMessage());
            return List.of();
        }
    }

    /** One contributor and their commit count on a repository. */
    public record ContributorStat(String login, int commits) {}

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
            OffsetDateTime pushedAt,
            /** Fork time for a fork. Paired with pushedAt it separates work from a bookmark. */
            OffsetDateTime createdAt
    ) {
        /**
         * True for a fork the student has actually pushed to since forking it.
         *
         * <p>GitHub's `fork` flag alone says nothing about who did the work, and the
         * ranking used to strip a repository's originality bonus on that flag alone.
         * The result put two bookmark forks of other people's repositories (30 points
         * each) above the student's own main project, forked from their team's
         * organisation and worked in daily (25).
         *
         * <p>A fresh fork inherits the upstream's `pushed_at`, which predates the fork
         * itself, so `pushedAt` after `createdAt` means commits of the student's own
         * landed in it. Missing timestamps answer false: without evidence of work, the
         * conservative reading is that this is someone else's repository.
         */
        public boolean isWorkedInFork() {
            return fork && pushedAt != null && createdAt != null && pushedAt.isAfter(createdAt);
        }
    }
}
