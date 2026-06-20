package com.inteliroadmap.backend.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
public class GithubApiClient {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${github.token:}")
    private String githubToken;

    public GithubApiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    public GithubRepoMetadata getRepoMetadata(String owner, String repo) {
        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo;
        ResponseEntity<String> repoResponse;
        
        try {
            repoResponse = restTemplate.exchange(apiUrl, HttpMethod.GET, entity, String.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "GitHub repository not found or is private.");
        } catch (Exception e) {
            log.error("Error fetching GitHub repo", e);
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
            log.error("Error parsing GitHub repo metadata", e);
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

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (githubToken != null && !githubToken.isBlank()) {
            headers.setBearerAuth(githubToken);
        }
        headers.set("Accept", "application/vnd.github.v3+json");
        return headers;
    }

    public record GithubRepoMetadata(String description, String defaultBranch, int stars, String homepage) {}
}
