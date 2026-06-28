package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.request.GithubImportRequest;
import com.inteliroadmap.backend.domain.dto.response.PortfolioResponse;
import com.inteliroadmap.backend.clients.GithubApiClient;
import com.inteliroadmap.backend.components.PortfolioAiAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class GithubPortfolioServiceImpl {

    private final GithubApiClient githubApiClient;
    private final PortfolioAiAnalyzer portfolioAiAnalyzer;

    public PortfolioResponse.PortfolioProjectResponse importFromGithub(GithubImportRequest request) {

        String repoUrl = request.getRepoUrl();
        
        // 1. Extract owner and repo from URL
        Pattern pattern = Pattern.compile("github\\.com/([^/]+)/([^/]+)");
        Matcher matcher = pattern.matcher(repoUrl);
        if (!matcher.find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid GitHub URL format. Example: https://github.com/facebook/react");
        }
        String owner = matcher.group(1);
        String repo = matcher.group(2).replace(".git", "");

        // 2. Fetch Repo Metadata via API Client
        GithubApiClient.GithubRepoMetadata metadata = githubApiClient.getRepoMetadata(owner, repo);

        // 3. Fetch README and optional package/build files
        String rawBaseUrl = "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + metadata.defaultBranch() + "/";
        String readmeContent = githubApiClient.fetchFileContent(rawBaseUrl + "README.md", 3000);
        
        String extraContext = githubApiClient.fetchFileContent(rawBaseUrl + "package.json", 1500);
        if (extraContext.isBlank()) {
            extraContext = githubApiClient.fetchFileContent(rawBaseUrl + "pom.xml", 1500);
        }
        if (extraContext.isBlank()) {
            extraContext = githubApiClient.fetchFileContent(rawBaseUrl + "build.gradle", 1500);
        }

        // 4. Use AI to summarize README and extract Tech Stack
        PortfolioAiAnalyzer.AiGithubSummary aiSummary = portfolioAiAnalyzer.analyzeGithubProject(
                repo, metadata.description(), readmeContent, extraContext
        );

        // 5. Build and return DTO (Do NOT save to DB yet)
        return PortfolioResponse.PortfolioProjectResponse.builder()
                .projectId(UUID.randomUUID()) // FE will replace or use this as temporary ID
                .projectName(repo)
                .repoUrl(repoUrl)
                .demoUrl(metadata.homepage())
                .description(aiSummary.summary() != null && !aiSummary.summary().isBlank() ? aiSummary.summary() : metadata.description())
                .stars(metadata.stars())
                .techStack(aiSummary.techStack())
                .icon("fab fa-github")
                .build();
    }
}
