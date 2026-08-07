package com.inteliroadmap.backend.ai.analyzer;

import com.inteliroadmap.backend.domain.dto.ai.SkillMatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Analyzes a GitHub repository with AI to extract its tech stack, generate a
 * portfolio summary and match it against a skill catalog.
 */
@Component
@Slf4j
public class PortfolioAiAnalyzer {

    /** Enough names to see what the model latched onto, without pasting a whole catalog into the log. */
    private static final int LOGGED_MATCHES = 10;

    private final ChatClient chatClient;
    private final String analyzePromptTemplate;
    private final String discoveryPromptTemplate;
    private final String modelName;

    public PortfolioAiAnalyzer(ChatClient chatClient,
                               @Value("classpath:prompts/github-portfolio-analysis.st") Resource analyzePrompt,
                               @Value("classpath:prompts/github-capability-discovery.st") Resource discoveryPrompt,
                               @Value("${spring.ai.openai.chat.options.model:unknown}") String modelName) {
        this.chatClient = chatClient;
        this.modelName = modelName;
        try {
            this.analyzePromptTemplate = analyzePrompt.getContentAsString(StandardCharsets.UTF_8);
            this.discoveryPromptTemplate = discoveryPrompt.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load github-portfolio-analysis prompt", e);
        }
    }

    /**
     * Reads implementation evidence before any catalog is shown. The result is used only
     * for retrieval; the catalog-constrained analysis remains the authority that creates
     * evidence. Separating retrieval from verification removes the need for an expanding
     * Java/Python/.NET regex registry and lets a newly seeded framework work immediately.
     */
    public CapabilityDiscovery discoverCapabilities(String repoName, String description,
                                                      String readmeContent, String extraContext,
                                                      RepoEvidence evidence) {
        RepoEvidence facts = evidence == null ? RepoEvidence.none() : evidence;
        try {
            CapabilityDiscovery result = chatClient.prompt()
                    .user(String.format(discoveryPromptTemplate, repoName, description,
                            facts.contributionText(), facts.languageText(), facts.commitText(),
                            readmeContent, extraContext))
                    .call()
                    .entity(CapabilityDiscovery.class);
            return result == null || result.capabilities() == null
                    ? CapabilityDiscovery.empty() : result;
        } catch (Exception e) {
            log.warn("PortfolioAiAnalyzer: capability discovery failed for {}: {}", repoName, e.getMessage());
            return CapabilityDiscovery.empty();
        }
    }

    /**
     * Which model answers {@link #analyzeGithubProject}. Recorded in the import audit,
     * because the same repository read by a different model is a different answer and
     * the outcome alone never says which one ran.
     */
    public String modelName() {
        return modelName;
    }

    /**
     * @param evidence what is known about the student's own work in this repository. The
     *                 prompt weighs it above the README, because a README describes a
     *                 project and this describes a person.
     */
    public AiGithubSummary analyzeGithubProject(String repoName, String description,
                                                 String readmeContent, String extraContext,
                                                 List<String> skillCatalog, RepoEvidence evidence) {
        log.info("PortfolioAiAnalyzer: Analyzing GitHub project: {} against {} catalog skill(s)",
                repoName, skillCatalog != null ? skillCatalog.size() : 0);
        String catalogText = (skillCatalog == null || skillCatalog.isEmpty())
                ? "(empty - return an empty matchedSkills list)"
                : String.join("\n", skillCatalog.stream().map(s -> "- " + s).toList());
        RepoEvidence facts = evidence == null ? RepoEvidence.none() : evidence;
        try {
            AiGithubSummary summary = chatClient.prompt()
                    .user(String.format(analyzePromptTemplate, catalogText, repoName, description,
                            facts.contributionText(), facts.languageText(), facts.commitText(),
                            readmeContent, extraContext))
                    .call()
                    .entity(AiGithubSummary.class);
            logOutcome(repoName, readmeContent, extraContext, summary);
            return summary;
        } catch (Exception e) {
            log.error("PortfolioAiAnalyzer: AI analysis failed for project: {}", repoName, e);
            return new AiGithubSummary("Project " + repoName + ": " + description, new HashMap<>(), List.of());
        }
    }

    /**
     * Records what the model was given and what it gave back.
     *
     * <p>Without this the analyzer logged its inputs' <em>sizes</em> and nothing else, so an
     * import that produced no skill matches was indistinguishable from one that produced
     * several: {@code SkillEvidenceService} logs only the rows it discards, and an empty
     * list is discarded at its first guard without a word. Three repositories were imported,
     * the summaries were written, and no evidence existed anywhere — with no way to tell
     * whether the model returned nothing or the evidence layer dropped it.
     *
     * <p>The README and config lengths are here for the same reason. A blank README means
     * the model was asked to match 466 catalog skills against a repository name alone, which
     * is a fetch problem wearing an AI problem's clothes.
     */
    private void logOutcome(String repoName, String readmeContent, String extraContext, AiGithubSummary summary) {
        if (summary == null) {
            log.warn("PortfolioAiAnalyzer: {} — model returned no parsable result", repoName);
            return;
        }
        List<SkillMatch> matches = summary.matchedSkills() == null ? List.of() : summary.matchedSkills();
        String named = matches.stream()
                .limit(LOGGED_MATCHES)
                .map(m -> m.skill() + "=" + m.confidence())
                .collect(Collectors.joining(", "));
        log.info("PortfolioAiAnalyzer: {} — readme {} char(s), config {} char(s), techStack {}, {} skill match(es){}",
                repoName,
                readmeContent == null ? 0 : readmeContent.length(),
                extraContext == null ? 0 : extraContext.length(),
                summary.techStack() == null ? "none" : summary.techStack().keySet(),
                matches.size(),
                named.isEmpty() ? "" : ": " + named + (matches.size() > LOGGED_MATCHES ? ", ..." : ""));
    }

    public record AiGithubSummary(String summary, Map<String, Object> techStack, List<SkillMatch> matchedSkills) {}

    public record CapabilitySignal(String name, String evidence) {}

    public record CapabilityDiscovery(List<CapabilitySignal> capabilities) {
        public static CapabilityDiscovery empty() {
            return new CapabilityDiscovery(List.of());
        }
    }

    /**
     * What is known about the student's own work, rendered for the prompt.
     *
     * <p>Each section says plainly when it is empty rather than being left blank. A gap in
     * a prompt is filled by the model's imagination; "GitHub returned no commit list" is a
     * fact it can reason from, and an absent heading is not.
     *
     * @param contributionSummary one line on their share, already phrased by the verifier
     * @param languageBytes       language → bytes, GitHub's measurement of the code itself
     * @param commitSubjects      their own commit subject lines, newest first
     */
    public record RepoEvidence(String contributionSummary, Map<String, Long> languageBytes,
                               List<String> commitSubjects) {

        public static RepoEvidence none() {
            return new RepoEvidence(null, Map.of(), List.of());
        }

        String contributionText() {
            return contributionSummary == null || contributionSummary.isBlank()
                    ? "(not established)"
                    : contributionSummary;
        }

        /**
         * Bytes with their share, because the raw numbers alone invite the wrong reading:
         * 40,000 bytes of Java means one thing next to 2,000 bytes of Shell and quite
         * another next to 900,000 bytes of TypeScript.
         */
        String languageText() {
            if (languageBytes == null || languageBytes.isEmpty()) {
                return "(GitHub returned no language breakdown)";
            }
            long total = languageBytes.values().stream().mapToLong(Long::longValue).sum();
            return languageBytes.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .map(entry -> String.format(java.util.Locale.ROOT, "- %s: %,d bytes (%.0f%%)",
                            entry.getKey(), entry.getValue(),
                            total > 0 ? 100.0 * entry.getValue() / total : 0.0))
                    .collect(Collectors.joining("\n"));
        }

        String commitText() {
            return commitSubjects == null || commitSubjects.isEmpty()
                    ? "(none found for this student in this repository)"
                    : commitSubjects.stream().map(s -> "- " + s).collect(Collectors.joining("\n"));
        }
    }
}
