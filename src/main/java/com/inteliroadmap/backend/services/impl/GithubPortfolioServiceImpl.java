package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.request.GithubImportRequest;
import com.inteliroadmap.backend.domain.dto.response.PortfolioResponse;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.enums.EvidenceType;
import com.inteliroadmap.backend.clients.GithubApiClient;
import com.inteliroadmap.backend.ai.analyzer.PortfolioAiAnalyzer;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.services.GithubPortfolioService;
import com.inteliroadmap.backend.services.SkillEvidenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of {@link GithubPortfolioService} that handles the integration
 * and analysis of GitHub repositories. It fetches repository metadata, reads
 * project files (like README, package.json), and uses AI to summarize the project
 * and extract its technology stack.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GithubPortfolioServiceImpl implements GithubPortfolioService {

    private final GithubApiClient githubApiClient;
    private final PortfolioAiAnalyzer portfolioAiAnalyzer;
    private final AuthenticatedStudentService authenticatedStudentService;
    private final SkillEvidenceService skillEvidenceService;

    /**
     * Imports and analyzes a GitHub repository based on the provided request.
     * Extracts repository information and uses AI to generate a summarized portfolio project view.
     * 
     * @param request The request containing the GitHub repository URL
     * @return A {@link PortfolioResponse.PortfolioProjectResponse} containing the analyzed project details
     * @throws ResponseStatusException if the provided GitHub URL format is invalid
     */
    @Override
    public PortfolioResponse.PortfolioProjectResponse importFromGithub(GithubImportRequest request) {

        String repoUrl = request.getRepoUrl();
        
        // 1. Extract owner and repo from URL
        // Define a regex pattern to extract the repository owner and name
        Pattern pattern = Pattern.compile("github\\.com/([^/]+)/([^/]+)");
        Matcher matcher = pattern.matcher(repoUrl);
        // Validate URL format and throw a 400 Bad Request if it doesn't match
        if (!matcher.find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid GitHub URL format. Example: https://github.com/facebook/react");
        }
        String owner = matcher.group(1);
        // Strip out the .git suffix if present to get the clean repo name
        String repo = matcher.group(2).replace(".git", "");

        // 2. Fetch Repo Metadata via API Client
        // Make an external API call to GitHub to retrieve repository metadata like description, stars, and default branch
        GithubApiClient.GithubRepoMetadata metadata = githubApiClient.getRepoMetadata(owner, repo);

        // 3. Fetch README and optional package/build files
        // Construct the base URL for fetching raw files from the repository's default branch
        String rawBaseUrl = "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + metadata.defaultBranch() + "/";
        // Attempt to fetch the README.md content with a size limit of 3000 chars
        String readmeContent = githubApiClient.fetchFileContent(rawBaseUrl + "README.md", 3000);
        
        // Fetch package.json for Node.js projects to determine dependencies and tech stack
        String extraContext = githubApiClient.fetchFileContent(rawBaseUrl + "package.json", 1500);
        // If package.json is not found, fallback to pom.xml for Maven/Java projects
        if (extraContext.isBlank()) {
            extraContext = githubApiClient.fetchFileContent(rawBaseUrl + "pom.xml", 1500);
        }
        // If still blank, try build.gradle for Gradle projects
        if (extraContext.isBlank()) {
            extraContext = githubApiClient.fetchFileContent(rawBaseUrl + "build.gradle", 1500);
        }

        // 4. Use AI to summarize README, extract Tech Stack, and match against the
        // student's career skill catalog in a single call.
        Student student = authenticatedStudentService.getRequiredStudent();
        UUID careerId = student.getCareerRole() != null ? student.getCareerRole().getCareerId() : null;
        List<String> catalog = skillEvidenceService.careerSkillCatalog(careerId);

        PortfolioAiAnalyzer.AiGithubSummary aiSummary = portfolioAiAnalyzer.analyzeGithubProject(
                repo, metadata.description(), readmeContent, extraContext, catalog
        );

        // 4b. Record AI-detected skills as PENDING evidence from the real repo read
        // (not the editable techStack field), so shortcuts can be suggested later.
        skillEvidenceService.recordEvidence(
                student.getUserId(), aiSummary.matchedSkills(), EvidenceType.GITHUB_PROJECT, null);

        // 5. Build and return DTO (Do NOT save the project to DB yet)
        // Construct the final response object with the extracted and AI-generated data
        return PortfolioResponse.PortfolioProjectResponse.builder()
                .projectId(UUID.randomUUID()) // FE will replace or use this as temporary ID
                .projectName(repo)
                .repoUrl(repoUrl)
                .demoUrl(metadata.homepage())
                // Use the AI summary if available; otherwise, fallback to the GitHub repo description
                .description(aiSummary.summary() != null && !aiSummary.summary().isBlank() ? aiSummary.summary() : metadata.description())
                .stars(metadata.stars())
                .techStack(aiSummary.techStack())
                .icon("fab fa-github")
                .build();
    }
}
