package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.response.portfolio.EvidenceSkillResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.MatchedSkillResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.PortfolioProjectResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.SourceReadResponse;

import com.inteliroadmap.backend.domain.dto.ai.SkillMatch;
import com.inteliroadmap.backend.domain.dto.request.GithubImportRequest;
import com.inteliroadmap.backend.domain.dto.response.portfolio.GithubImportAuditResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.GithubRepoRankResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.PortfolioResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.RepoEvidenceResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.RepoSourcePlanResponse;
import com.inteliroadmap.backend.domain.entity.GithubImportAudit;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentSkillEvidence;
import com.inteliroadmap.backend.domain.enums.EvidenceStatus;
import com.inteliroadmap.backend.domain.enums.EvidenceType;
import com.inteliroadmap.backend.repositories.GithubImportAuditRepository;
import com.inteliroadmap.backend.repositories.StudentSkillEvidenceRepository;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.repositories.PortfolioProjectRepository;
import com.inteliroadmap.backend.clients.GithubApiClient;
import com.inteliroadmap.backend.components.RepoAuthorshipVerifier;
import com.inteliroadmap.backend.components.RepoSkillCandidateSelector;
import com.inteliroadmap.backend.components.RoadmapRefreshTrigger;
import com.inteliroadmap.backend.components.SkillProficiencyPromoter;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private static final int README_CHARS = 3000;
    private static final int BUILD_FILE_CHARS = 1500;
    /** Tried in order; the first one with content becomes the build/dependency context. */
    private static final List<String> BUILD_FILES = List.of(
            "package.json", "pom.xml", "build.gradle", "build.gradle.kts",
            "requirements.txt", "pyproject.toml", "go.mod", "Cargo.toml");
    /** Runtime/infrastructure files prove configured use that a dependency list cannot. */
    private static final int INFRA_FILE_CHARS = 1200;
    private static final int INFRA_CONTEXT_CHARS = 4800;
    private static final List<String> INFRA_FILES = List.of(
            "src/main/resources/application.yaml",
            "src/main/resources/application.yml",
            "src/main/resources/application.properties",
            "docker-compose.yml",
            "docker-compose.yaml",
            ".env.example");
    // A role-diverse sample is deliberately larger than the old five 850-character
    // snippets: those often ended before a method body and made substantial projects
    // look like dependency manifests. This remains bounded for predictable model cost.
    private static final int SOURCE_FILE_CHARS = 1400;
    private static final int SOURCE_FILES_LIMIT = 10;
    /** Production source families represented by the roadmap's language choices. */
    private static final List<String> SOURCE_EXTENSIONS = List.of(
            ".java", ".kt", ".kts", ".scala",
            ".cs", ".fs", ".fsx", ".vb",
            ".go", ".rs", ".py", ".pyi",
            ".ts", ".tsx", ".js", ".jsx", ".mjs", ".cjs",
            ".php", ".rb", ".swift", ".gd",
            ".c", ".h", ".cc", ".cpp", ".cxx", ".hpp", ".hxx",
            ".vue", ".svelte");
    /** Enough of the student's commits to show what they worked on, without flooding the prompt. */
    private static final int COMMIT_SUBJECTS = 20;
    /**
     * Skill names the prompt has room for.
     *
     * <p>Same number as the old blanket cap, so the prompt costs exactly what it did.
     * What changed is which 200 — chosen for the repository rather than by the alphabet.
     */
    private static final int PROMPT_CATALOG_LIMIT = 200;

    private final GithubApiClient githubApiClient;
    private final PortfolioAiAnalyzer portfolioAiAnalyzer;
    private final AuthenticatedStudentService authenticatedStudentService;
    private final SkillEvidenceService skillEvidenceService;
    private final TokenCipher tokenCipher;
    private final GithubRepoRankingService githubRepoRankingService;
    private final RoadmapRefreshTrigger roadmapRefreshTrigger;
    private final SkillProficiencyPromoter skillProficiencyPromoter;
    private final GithubImportAuditRepository githubImportAuditRepository;
    private final StudentSkillEvidenceRepository studentSkillEvidenceRepository;
    private final RepoAuthorshipVerifier repoAuthorshipVerifier;
    private final RepoSkillCandidateSelector repoSkillCandidateSelector;
    private final StudentRepository studentRepository;
    private final PortfolioProjectRepository portfolioProjectRepository;

    /**
     * Imports and analyzes a GitHub repository based on the provided request.
     * Extracts repository information and uses AI to generate a summarized portfolio project view.
     *
     * @param request The request containing the GitHub repository URL
     * @return A {@link PortfolioProjectResponse} containing the analyzed project details
     * @throws ResponseStatusException if the provided GitHub URL format is invalid
     */
    @Override
    public PortfolioProjectResponse importFromGithub(GithubImportRequest request) {
        Student student = authenticatedStudentService.getRequiredStudent();
        List<String> catalog = careerCatalog(student);
        PortfolioProjectResponse project = analyzeRepo(request.getRepoUrl(), student, catalog);
        // The point of importing a project is that the roadmap reacts to it. Without
        // this the evidence just sat PENDING and the student saw no change at all.
        roadmapRefreshTrigger.refreshCurrentStudent("github-import");
        return project;
    }

    @Override
    public List<GithubRepoRankResponse> listRankedRepos() {
        Student student = authenticatedStudentService.getRequiredStudent();
        String accessToken = resolveGithubToken(student);
        List<GithubApiClient.GithubRepoSummary> repos = githubApiClient.listOwnedRepos(accessToken);
        return githubRepoRankingService.rank(repos, careerCatalog(student));
    }

    @Override
    public List<PortfolioProjectResponse> importBatch(List<String> repoUrls) {
        if (repoUrls == null || repoUrls.isEmpty()) {
            return List.of();
        }
        Student student = authenticatedStudentService.getRequiredStudent();
        List<String> catalog = careerCatalog(student);

        List<PortfolioProjectResponse> results = new ArrayList<>();
        for (String repoUrl : repoUrls) {
            try {
                results.add(analyzeRepo(repoUrl, student, catalog));
            } catch (Exception e) {
                // One bad repo shouldn't sink the whole batch — skip it and keep going.
                log.warn("GithubPortfolioServiceImpl: skipping repo '{}' in batch import: {}", repoUrl, e.getMessage());
            }
        }
        // Once for the whole batch, after every repo has contributed its evidence —
        // refreshing per repo would re-run the engine N times for the same result.
        if (!results.isEmpty()) {
            roadmapRefreshTrigger.refreshCurrentStudent("github-import-batch");
        }
        return results;
    }

    /**
     * Core per-repo analysis shared by single and batch import: parse the URL, fetch metadata +
     * README + build file, run the AI summary against the student's skill catalog, record matched
     * skills as PENDING evidence, and build the (unsaved) project DTO.
     */
    private PortfolioProjectResponse analyzeRepo(String repoUrl, Student student, List<String> catalog) {
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

        // Anonymous reads go through the raw host (cheap, no API rate cost); it needs the
        // default branch, which is why this is built even when the token path is taken.
        String rawBaseUrl = "https://raw.githubusercontent.com/" + owner + "/" + repo + "/"
                + metadata.defaultBranch() + "/";

        // Every file attempt is recorded, not just the one that won, so the audit can show
        // an empty README as an empty README rather than as a model that found nothing.
        List<GithubImportAudit.SourceRead> sources = new ArrayList<>();
        String readmeContent = readSource(sources, "README.md", README_CHARS, owner, repo, token, rawBaseUrl);
        String extraContext = "";
        for (String buildFile : BUILD_FILES) {
            extraContext = readSource(sources, buildFile, BUILD_FILE_CHARS, owner, repo, token, rawBaseUrl);
            if (!extraContext.isBlank()) {
                break;
            }
        }
        StringBuilder evidenceContext = new StringBuilder(extraContext);
        for (String infraFile : INFRA_FILES) {
            String content = readSource(sources, infraFile, INFRA_FILE_CHARS,
                    owner, repo, token, rawBaseUrl);
            if (content.isBlank()) {
                continue;
            }
            String block = "\n\n--- " + infraFile + " ---\n" + content;
            int remaining = INFRA_CONTEXT_CHARS - evidenceContext.length();
            if (remaining <= 0) {
                break;
            }
            evidenceContext.append(block, 0, Math.min(block.length(), remaining));
        }
        for (String sourcePath : representativeSourcePaths(
                githubApiClient.listRepositoryFiles(owner, repo, metadata.defaultBranch(), token))) {
            String content = readSource(sources, sourcePath, SOURCE_FILE_CHARS,
                    owner, repo, token, rawBaseUrl);
            if (!content.isBlank()) {
                evidenceContext.append("\n\n--- representative source: ")
                        .append(sourcePath).append(" ---\n").append(content);
            }
        }
        extraContext = evidenceContext.toString();

        // Who wrote this, before asking what it demonstrates. Three cheap calls that turn
        // "this repository uses React" into "you wrote 143 of its 512 commits" — the
        // difference between summarising a project and verifying a person's work.
        RepoAuthorshipVerifier.Authorship authorship = repoAuthorshipVerifier.verify(
                githubApiClient.listContributors(owner, repo, token), student.getGithubLogin());
        Map<String, Long> languageBytes = githubApiClient.fetchLanguageBytes(owner, repo, token);
        List<String> commitSubjects = githubApiClient.listCommitMessagesByAuthor(
                owner, repo, authorship.authorLogin(), COMMIT_SUBJECTS, token);
        List<String> repoSignals = new ArrayList<>(commitSubjects);
        repoSignals.add(readmeContent);
        repoSignals.add(extraContext);
        repoSignals.add(metadata.description());

        // First discover implementation capabilities without showing the model a catalog.
        // This avoids both hard-coded framework rules and the circular failure where a
        // skill cannot be selected because its name was not already in the shortlist.
        PortfolioAiAnalyzer.CapabilityDiscovery discovery = portfolioAiAnalyzer.discoverCapabilities(
                repo, metadata.description(), readmeContent, extraContext,
                new PortfolioAiAnalyzer.RepoEvidence(authorship.reason(), languageBytes, commitSubjects));
        discovery.capabilities().forEach(capability -> {
            repoSignals.add(capability.name());
            repoSignals.add(capability.evidence());
        });

        // Which of the career's skills are worth a prompt slot for THIS repository. The
        // full catalog cannot be sent and truncating it by rank runs alphabetically past
        // the sixtieth name, so the repository's own text decides instead.
        List<String> promptCatalog = repoSkillCandidateSelector.select(
                catalog, repoSignals, languageBytes, PROMPT_CATALOG_LIMIT);

        PortfolioAiAnalyzer.AiGithubSummary aiSummary = portfolioAiAnalyzer.analyzeGithubProject(
                repo, metadata.description(), readmeContent, extraContext, promptCatalog,
                new PortfolioAiAnalyzer.RepoEvidence(authorship.reason(), languageBytes, commitSubjects));

        recordAudit(student, repoUrl, owner + "/" + repo, sources, promptCatalog, aiSummary,
                authorship, languageBytes, commitSubjects);

        // A repository this student demonstrably did not write must not raise their level.
        // The project itself is still imported — they may well want a team project on their
        // portfolio — but it earns no skill evidence and no promotion.
        //
        // Only a POSITIVE finding blocks. UNKNOWN passes through deliberately: a rate limit,
        // an outage, or a repository whose statistics GitHub declined to compute would
        // otherwise be indistinguishable from a student padding their portfolio, and the
        // system would silently side against them on the strength of a failed request.
        if (authorship.verdict() == RepoAuthorshipVerifier.Verdict.NOT_CONTRIBUTED) {
            log.info("GithubPortfolioServiceImpl: '{}' imported without evidence — {}",
                    repoUrl, authorship.reason());
            return buildProject(repo, repoUrl, metadata, aiSummary);
        }

        List<UUID> evidenceIds = skillEvidenceService.recordEvidence(
                student.getUserId(), aiSummary.matchedSkills(), EvidenceType.GITHUB_PROJECT, null, repoUrl);

        // Raise the profile straight from the evidence, rather than waiting for the
        // roadmap refresh below to complete nodes first. A student connects GitHub
        // precisely because they have not worked through the roadmap yet, so gating
        // promotion on node completion meant a synced repository moved nothing: no
        // verified_by, verified share stuck at 0.00, and SeniorityCalculator's
        // VERIFIED_FLOOR capping them at JUNIOR no matter how much they had built.
        // Isolated because a failure here must not lose the analysis the student
        // just paid a model call for.
        try {
            skillProficiencyPromoter.promoteFromEvidence(student.getUserId(), evidenceIds);
        } catch (Exception e) {
            log.warn("GithubPortfolioServiceImpl: could not promote proficiency from {} evidence "
                    + "row(s) for user {}: {}", evidenceIds.size(), student.getUserId(), e.getMessage());
        }

        return buildProject(repo, repoUrl, metadata, aiSummary);
    }

    /** The portfolio entry, built the same way whether or not evidence was recorded. */
    private PortfolioProjectResponse buildProject(
            String repo, String repoUrl, GithubApiClient.GithubRepoMetadata metadata,
            PortfolioAiAnalyzer.AiGithubSummary aiSummary) {
        return PortfolioProjectResponse.builder()
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

    @Override
    public GithubImportAuditResponse getImportAudit(String repoUrl) {
        Student student = authenticatedStudentService.getRequiredStudent();
        return getImportAuditForStudent(student.getUserId(), repoUrl);
    }

    @Override
    public GithubImportAuditResponse getImportAuditForStudent(UUID studentId, String repoUrl) {
        GithubImportAudit audit = githubImportAuditRepository
                .findByUserIdAndRepoUrl(studentId, repoUrl)
                .orElse(null);
        if (audit == null) {
            return null;
        }

        // Status is read now, not remembered. The promoter can supersede a row hours after
        // the import, so a stored copy would end up telling the student a skill was accepted
        // while their own profile shows otherwise.
        Map<String, EvidenceStatus> statusBySkill = skillEvidenceStatuses(studentId, repoUrl);

        List<SkillMatch> matches = audit.getMatchedSkills() == null ? List.of() : audit.getMatchedSkills();
        List<MatchedSkillResponse> skills = matches.stream()
                .map(match -> MatchedSkillResponse.builder()
                        .skill(match.skill())
                        .confidence(match.confidence())
                        // No evidence row for a skill the model named means the evidence layer
                        // refused it — it was not in the catalog, and inventing a skill from a
                        // model's guess is exactly what that layer exists to prevent.
                        .status(statusBySkill.containsKey(normalizeSkill(match.skill()))
                                ? statusBySkill.get(normalizeSkill(match.skill())).name()
                                : "NOT_RECORDED")
                        .build())
                .toList();

        List<GithubImportAudit.SourceRead> sources = audit.getSources() == null ? List.of() : audit.getSources();
        return GithubImportAuditResponse.builder()
                .repoUrl(audit.getRepoUrl())
                .repoFullName(audit.getRepoFullName())
                .analyzedAt(audit.getAnalyzedAt() != null ? audit.getAnalyzedAt().toString() : null)
                .model(audit.getModel())
                .fetchMode(audit.getFetchMode())
                .catalogSize(audit.getCatalogSize() == null ? 0 : audit.getCatalogSize())
                .careerName(audit.getCareerName())
                .sources(sources.stream()
                        .map(source -> SourceReadResponse.builder()
                                .path(source.path())
                                .chars(source.chars())
                                .found(source.found())
                                .build())
                        .toList())
                .summary(audit.getSummary())
                .techStack(audit.getTechStack())
                .skills(skills)
                .authorshipVerdict(audit.getAuthorshipVerdict())
                .authorLogin(audit.getAuthorLogin())
                .authorCommits(audit.getAuthorCommits() == null ? 0 : audit.getAuthorCommits())
                .totalCommits(audit.getTotalCommits() == null ? 0 : audit.getTotalCommits())
                .authorshipReason(audit.getAuthorshipReason())
                .evidenceBlocked(Boolean.TRUE.equals(audit.getEvidenceBlocked()))
                .languageBytes(audit.getLanguageBytes())
                .commitSubjects(audit.getCommitSubjects())
                .build();
    }

    @Override
    public RepoEvidenceResponse getRepoEvidence(String repoUrl) {
        Student student = authenticatedStudentService.getRequiredStudent();
        return describeEvidence(student.getUserId(), repoUrl);
    }

    @Override
    public RepoEvidenceResponse withdrawRepoEvidence(String repoUrl) {
        Student student = authenticatedStudentService.getRequiredStudent();
        // Described before the deletion, because after it there is nothing left to
        // describe and the student is owed the list of what they just gave up.
        RepoEvidenceResponse lost = describeEvidence(student.getUserId(), repoUrl);
        skillEvidenceService.withdrawEvidenceFrom(student.getUserId(), EvidenceType.GITHUB_PROJECT, repoUrl);
        return lost;
    }

    /**
     * Reads the live evidence rows for one repository into the response shape.
     *
     * <p>Deliberately reads the evidence table rather than the import audit. The audit
     * records what the model <em>claimed</em>; a claim the evidence layer refused (a
     * skill outside the catalog) never reached the profile and cannot be lost by
     * deleting the project. Listing it would inflate the warning with skills the student
     * never had.
     */
    private RepoEvidenceResponse describeEvidence(UUID userId, String repoUrl) {
        Map<String, EvidenceStatus> statuses = skillEvidenceStatuses(userId, repoUrl);

        // The map is keyed on the normalised name, so the display name comes back off the
        // rows themselves — a student should read "Spring Boot", not "spring boot".
        Map<String, String> displayNames = new HashMap<>();
        for (StudentSkillEvidence evidence : studentSkillEvidenceRepository
                .findByUserIdAndSourceUrl(userId, repoUrl)) {
            if (evidence.getSkillName() != null) {
                displayNames.putIfAbsent(normalizeSkill(evidence.getSkillName()), evidence.getSkillName());
            }
        }

        List<EvidenceSkillResponse> skills = statuses.entrySet().stream()
                .map(entry -> EvidenceSkillResponse.builder()
                        .skill(displayNames.getOrDefault(entry.getKey(), entry.getKey()))
                        .status(entry.getValue().name())
                        .build())
                .sorted(Comparator.comparing(EvidenceSkillResponse::getSkill))
                .toList();

        int verifying = (int) statuses.values().stream()
                .filter(status -> status == EvidenceStatus.ACCEPTED)
                .count();

        return RepoEvidenceResponse.builder()
                .repoUrl(repoUrl)
                .verifyingCount(verifying)
                .skills(skills)
                .build();
    }

    /**
     * Current status of every evidence row this repository produced, keyed by skill name.
     *
     * <p>Keyed case-insensitively because the model returns the catalog's spelling only
     * most of the time, and a case mismatch here would silently report a recorded skill
     * as NOT_RECORDED — the audit would accuse the evidence layer of dropping something
     * it actually kept.
     */
    private Map<String, EvidenceStatus> skillEvidenceStatuses(UUID userId, String repoUrl) {
        Map<String, EvidenceStatus> statuses = new HashMap<>();
        for (StudentSkillEvidence evidence : studentSkillEvidenceRepository
                .findByUserIdAndSourceUrl(userId, repoUrl)) {
            if (evidence.getSkillName() == null || evidence.getStatus() == null) {
                continue;
            }
            // One skill can hold several rows across re-imports; ACCEPTED is the outcome
            // that matters to the student, so it wins over a superseded sibling.
            statuses.merge(normalizeSkill(evidence.getSkillName()), evidence.getStatus(),
                    (existing, incoming) -> existing == EvidenceStatus.ACCEPTED ? existing : incoming);
        }
        return statuses;
    }

    private static String normalizeSkill(String skillName) {
        return skillName == null ? "" : skillName.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public GithubImportAuditResponse getPublicImportAudit(String portfolioSlug, String repoUrl) {
        Student student = studentRepository.findByPortfolioSlug(portfolioSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found"));
        String requested = normalizeRepoUrl(repoUrl);
        boolean displayed = portfolioProjectRepository.findByUser_UserId(student.getUserId()).stream()
                .anyMatch(project -> normalizeRepoUrl(project.getRepoUrl()).equals(requested));
        if (!displayed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project evidence not found");
        }
        GithubImportAuditResponse audit = getImportAuditForStudent(student.getUserId(), repoUrl);
        if (audit == null) return null;

        // Public proof explains the conclusion without publishing private working data.
        audit.setAuthorLogin(null);
        audit.setCommitSubjects(null);
        audit.setAuthorshipReason(publicAuthorshipReason(audit));
        audit.setSources(audit.getSources() == null ? List.of() : audit.getSources().stream()
                .filter(SourceReadResponse::isFound)
                .toList());
        return audit;
    }

    private static String publicAuthorshipReason(GithubImportAuditResponse audit) {
        if ("CONTRIBUTED".equals(audit.getAuthorshipVerdict()) && audit.getTotalCommits() > 0) {
            return String.format(Locale.ROOT, "The portfolio owner authored %d of %d commits (%.0f%%).",
                    audit.getAuthorCommits(), audit.getTotalCommits(),
                    100.0 * audit.getAuthorCommits() / audit.getTotalCommits());
        }
        if ("NOT_CONTRIBUTED".equals(audit.getAuthorshipVerdict())) {
            return "GitHub did not attribute repository commits to the portfolio owner.";
        }
        return "GitHub authorship could not be established for this analysis.";
    }

    private static String normalizeRepoUrl(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("(?i)\\.git$", "").replaceAll("/$", "").toLowerCase(Locale.ROOT);
    }

    @Override
    public List<RepoSourcePlanResponse> planBatchAnalysis(List<String> repoUrls) {
        if (repoUrls == null || repoUrls.isEmpty()) {
            return List.of();
        }
        Student student = authenticatedStudentService.getRequiredStudent();
        String token = studentGithubTokenOrNull(student);
        List<RepoSourcePlanResponse> plans = new ArrayList<>();
        for (String repoUrl : repoUrls) {
            Matcher matcher = GITHUB_URL_PATTERN.matcher(repoUrl == null ? "" : repoUrl);
            if (!matcher.find()) {
                continue;
            }
            String owner = matcher.group(1);
            String repo = matcher.group(2).replace(".git", "");
            try {
                GithubApiClient.GithubRepoMetadata metadata = githubApiClient.getRepoMetadata(owner, repo, token);
                List<String> sourcePaths = representativeSourcePaths(githubApiClient.listRepositoryFiles(
                        owner, repo, metadata.defaultBranch(), token));
                plans.add(RepoSourcePlanResponse.builder()
                        .repoUrl(repoUrl)
                        .repoFullName(owner + "/" + repo)
                        .sourcePaths(sourcePaths)
                        .build());
            } catch (Exception e) {
                log.warn("GithubPortfolioServiceImpl: could not plan source context for '{}': {}",
                        repoUrl, e.getMessage());
            }
        }
        return plans;
    }

    /** Selects a small, language-agnostic sample spanning architectural roles. */
    static List<String> representativeSourcePaths(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return List.of();
        }
        List<String> candidates = paths.stream()
                .filter(GithubPortfolioServiceImpl::isProductionSource)
                .sorted(Comparator.comparingInt(GithubPortfolioServiceImpl::sourceSignalScore).reversed()
                        .thenComparingInt(String::length)
                        .thenComparing(String.CASE_INSENSITIVE_ORDER))
                .toList();
        // One bucket earns at most one slot. Combining controller, app and main in one
        // bucket previously let an application entrypoint consume the HTTP slot; combining
        // service, client and API similarly hid the persistence-facing implementation.
        // The model then received ten Java files but no controller/repository/test and could
        // only say "Java". These are architectural roles, not framework names.
        List<List<String>> roles = List.of(
                List.of("controller", "handler", "route", "endpoint"),
                List.of("entity", "model", "domain", "schema"),
                List.of("repository", "database", "/db/", "dao", "persistence", "dbcontext", "migration"),
                List.of("service", "usecase", "business", "worker"),
                List.of("test", "spec"),
                List.of("middleware", "security", "auth"),
                List.of("client", "/api/"),
                List.of("hook", "store", "context", "reducer"),
                List.of("config"),
                List.of("page", "component", "view", "app", "main"));
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (List<String> roleSignals : roles) {
            candidates.stream()
                    .filter(path -> roleSignals.stream().anyMatch(signal -> roleMatches(path, signal)))
                    .filter(path -> !selected.contains(path))
                    .findFirst()
                    .ifPresent(selected::add);
        }
        for (String candidate : candidates) {
            if (selected.size() >= SOURCE_FILES_LIMIT) break;
            selected.add(candidate);
        }
        return selected.stream().limit(SOURCE_FILES_LIMIT).toList();
    }

    private static boolean roleMatches(String path, String signal) {
        String p = path.toLowerCase(Locale.ROOT).replace('\\', '/');
        if ("main".equals(signal) || "app".equals(signal)) {
            String fileName = p.substring(p.lastIndexOf('/') + 1);
            String baseName = fileName.contains(".")
                    ? fileName.substring(0, fileName.indexOf('.')) : fileName;
            return baseName.equals(signal) || p.contains("/" + signal + "/");
        }
        return p.contains(signal);
    }

    static boolean isProductionSource(String path) {
        if (path == null) return false;
        String p = path.toLowerCase(Locale.ROOT).replace('\\', '/');
        if (p.startsWith("vendor/")
                || p.startsWith("node_modules/") || p.startsWith("generated/")
                || p.contains("/vendor/")
                || p.contains("/node_modules/") || p.contains("/generated/")
                || p.endsWith(".min.js") || p.endsWith(".d.ts")) {
            return false;
        }
        return SOURCE_EXTENSIONS.stream().anyMatch(p::endsWith);
    }

    private static int sourceSignalScore(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String signal : List.of("repository", "service", "controller", "entity",
                "model", "database", "client", "api", "main", "app")) {
            if (p.contains(signal)) score += 10;
        }
        score -= (int) p.chars().filter(ch -> ch == '/').count();
        return score;
    }

    /**
     * Reads one repository file and notes the attempt.
     *
     * <p>Both branches are kept because they see different things: the token path reads
     * private repositories, and the anonymous path silently returns nothing for them.
     * Which path ran is therefore part of the audit, not an implementation detail.
     */
    private String readSource(List<GithubImportAudit.SourceRead> sources, String path, int maxChars,
                              String owner, String repo, String token, String rawBaseUrl) {
        String content = token != null
                ? githubApiClient.fetchRepoFile(owner, repo, path, maxChars, token)
                : githubApiClient.fetchFileContent(rawBaseUrl + path, maxChars);
        if (content == null) {
            content = "";
        }
        sources.add(new GithubImportAudit.SourceRead(path, content.length(), !content.isBlank()));
        return content;
    }

    /**
     * Stores what this run read and what came back.
     *
     * <p>Isolated the same way promotion is: a failure to write the audit must not lose
     * the analysis the student just paid a model call for. An import with no audit is a
     * gap on one screen; a lost import is lost work.
     */
    private void recordAudit(Student student, String repoUrl, String repoFullName,
                             List<GithubImportAudit.SourceRead> sources, List<String> catalog,
                             PortfolioAiAnalyzer.AiGithubSummary aiSummary,
                             RepoAuthorshipVerifier.Authorship authorship,
                             Map<String, Long> languageBytes, List<String> commitSubjects) {
        try {
            GithubImportAudit audit = githubImportAuditRepository
                    .findByUserIdAndRepoUrl(student.getUserId(), repoUrl)
                    .orElseGet(GithubImportAudit::new);
            audit.setUserId(student.getUserId());
            audit.setRepoUrl(repoUrl);
            audit.setRepoFullName(repoFullName);
            audit.setAnalyzedAt(LocalDateTime.now());
            audit.setModel(portfolioAiAnalyzer.modelName());
            audit.setFetchMode(studentGithubTokenOrNull(student) != null ? "AUTHENTICATED" : "ANONYMOUS");
            audit.setCatalogSize(catalog == null ? 0 : catalog.size());
            audit.setCareerName(student.getCareerRole() != null ? student.getCareerRole().getCareerName() : null);
            audit.setSources(sources);
            audit.setSummary(aiSummary.summary());
            audit.setTechStack(aiSummary.techStack());
            audit.setMatchedSkills(aiSummary.matchedSkills());
            audit.setAuthorshipVerdict(authorship.verdict().name());
            audit.setAuthorLogin(authorship.authorLogin());
            audit.setAuthorCommits(authorship.authorCommits());
            audit.setTotalCommits(authorship.totalCommits());
            audit.setAuthorshipReason(authorship.reason());
            audit.setLanguageBytes(languageBytes);
            audit.setCommitSubjects(commitSubjects);
            audit.setEvidenceBlocked(
                    authorship.verdict() == RepoAuthorshipVerifier.Verdict.NOT_CONTRIBUTED);
            githubImportAuditRepository.save(audit);
        } catch (Exception e) {
            log.warn("GithubPortfolioServiceImpl: could not record the import audit for '{}': {}",
                    repoUrl, e.getMessage());
        }
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
