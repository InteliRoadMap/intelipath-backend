package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.request.GithubImportRequest;
import com.inteliroadmap.backend.domain.dto.response.portfolio.GithubRepoRankResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.PortfolioResponse;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.enums.EvidenceType;
import com.inteliroadmap.backend.clients.GithubApiClient;
import com.inteliroadmap.backend.ai.analyzer.PortfolioAiAnalyzer;
import com.inteliroadmap.backend.security.TokenCipher;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.services.GithubPortfolioService;
import com.inteliroadmap.backend.services.SkillEvidenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
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

    private static final Pattern GITHUB_URL_PATTERN = Pattern.compile("github\\.com/([^/]+)/([^/]+)");

    private final GithubApiClient githubApiClient;
    private final PortfolioAiAnalyzer portfolioAiAnalyzer;
    private final AuthenticatedStudentService authenticatedStudentService;
    private final SkillEvidenceService skillEvidenceService;
    private final TokenCipher tokenCipher;
    private final GithubRepoRankingService githubRepoRankingService;

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
        Student student = authenticatedStudentService.getRequiredStudent();
        List<String> catalog = careerCatalog(student);
        return analyzeRepo(request.getRepoUrl(), student, catalog);
    }

    @Override
    public List<GithubRepoRankResponse> listRankedRepos() {
        Student student = authenticatedStudentService.getRequiredStudent();
        String accessToken = resolveGithubToken(student);
        List<GithubApiClient.GithubRepoSummary> repos = githubApiClient.listOwnedRepos(accessToken);
        return githubRepoRankingService.rank(repos, careerCatalog(student));
    }

    @Override
    public List<PortfolioResponse.PortfolioProjectResponse> importBatch(List<String> repoUrls) {
        if (repoUrls == null || repoUrls.isEmpty()) {
            return List.of();
        }
        Student student = authenticatedStudentService.getRequiredStudent();
        List<String> catalog = careerCatalog(student);

        List<PortfolioResponse.PortfolioProjectResponse> results = new ArrayList<>();
        for (String repoUrl : repoUrls) {
            try {
                results.add(analyzeRepo(repoUrl, student, catalog));
            } catch (Exception e) {
                // One bad repo shouldn't sink the whole batch — skip it and keep going.
                log.warn("GithubPortfolioServiceImpl: skipping repo '{}' in batch import: {}", repoUrl, e.getMessage());
            }
        }
        return results;
    }

    /**
     * Core per-repo analysis shared by single and batch import: parse the URL, fetch metadata +
     * README + build file, run the AI summary against the student's skill catalog, record matched
     * skills as PENDING evidence, and build the (unsaved) project DTO.
     */
    private PortfolioResponse.PortfolioProjectResponse analyzeRepo(String repoUrl, Student student, List<String> catalog) {
        Matcher matcher = GITHUB_URL_PATTERN.matcher(repoUrl);
        if (!matcher.find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid GitHub URL format. Example: https://github.com/facebook/react");
        }
        String owner = matcher.group(1);
        String repo = matcher.group(2).replace(".git", "");

        // The student's own token (if they've connected GitHub) lets us read PRIVATE repos too.
        // Null for an anonymous single public-URL import — falls back to the app/anon path.
        String token = studentGithubTokenOrNull(student);

        GithubApiClient.GithubRepoMetadata metadata = githubApiClient.getRepoMetadata(owner, repo, token);

        String readmeContent;
        String extraContext;
        if (token != null) {
            // Authenticated Contents API — works for private and public repos alike.
            readmeContent = githubApiClient.fetchRepoFile(owner, repo, "README.md", 3000, token);
            extraContext = githubApiClient.fetchRepoFile(owner, repo, "package.json", 1500, token);
            if (extraContext.isBlank()) {
                extraContext = githubApiClient.fetchRepoFile(owner, repo, "pom.xml", 1500, token);
            }
            if (extraContext.isBlank()) {
                extraContext = githubApiClient.fetchRepoFile(owner, repo, "build.gradle", 1500, token);
            }
        } else {
            // Anonymous public read via the raw host (cheap, no API rate cost).
            String rawBaseUrl = "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + metadata.defaultBranch() + "/";
            readmeContent = githubApiClient.fetchFileContent(rawBaseUrl + "README.md", 3000);
            extraContext = githubApiClient.fetchFileContent(rawBaseUrl + "package.json", 1500);
            if (extraContext.isBlank()) {
                extraContext = githubApiClient.fetchFileContent(rawBaseUrl + "pom.xml", 1500);
            }
            if (extraContext.isBlank()) {
                extraContext = githubApiClient.fetchFileContent(rawBaseUrl + "build.gradle", 1500);
            }
        }

        PortfolioAiAnalyzer.AiGithubSummary aiSummary = portfolioAiAnalyzer.analyzeGithubProject(
                repo, metadata.description(), readmeContent, extraContext, catalog);

        skillEvidenceService.recordEvidence(
                student.getUserId(), aiSummary.matchedSkills(), EvidenceType.GITHUB_PROJECT, null);

        return PortfolioResponse.PortfolioProjectResponse.builder()
                .projectId(UUID.randomUUID())
                .projectName(repo)
                .repoUrl(repoUrl)
                .demoUrl(metadata.homepage())
                .description(aiSummary.summary() != null && !aiSummary.summary().isBlank()
                        ? aiSummary.summary() : metadata.description())
                .stars(metadata.stars())
                .techStack(aiSummary.techStack())
                .icon("fab fa-github")
                .build();
    }

    private List<String> careerCatalog(Student student) {
        UUID careerId = student.getCareerRole() != null ? student.getCareerRole().getCareerId() : null;
        return skillEvidenceService.careerSkillCatalog(careerId);
    }

    /**
     * Decrypts the student's stored GitHub sync token (set by the explicit Connect-GitHub link
     * flow, not login). Fails with a clear 400 when they haven't connected GitHub yet, so the
     * frontend can show the "Connect GitHub" prompt.
     */
    /** Decrypted GitHub token for the student, or null if none/undecryptable (no exception). */
    private String studentGithubTokenOrNull(Student student) {
        if (!tokenCipher.isEnabled() || student.getGithubSyncTokenEnc() == null) {
            return null;
        }
        String token = tokenCipher.decrypt(student.getGithubSyncTokenEnc());
        return (token != null && !token.isBlank()) ? token : null;
    }

    private String resolveGithubToken(Student student) {
        if (!tokenCipher.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GitHub sync is not configured on this server.");
        }
        String encrypted = student.getGithubSyncTokenEnc();
        String token = encrypted != null ? tokenCipher.decrypt(encrypted) : null;
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No GitHub account connected. Please connect GitHub to sync your repositories.");
        }
        return token;
    }
}
