package com.inteliroadmap.backend.ai.analyzer;

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

/**
 * Analyzes a GitHub repository with AI to extract its tech stack, generate a
 * portfolio summary and match it against a skill catalog.
 */
@Component
@Slf4j
public class PortfolioAiAnalyzer {

    private final ChatClient chatClient;
    private final String analyzePromptTemplate;

    public PortfolioAiAnalyzer(ChatClient chatClient,
                               @Value("classpath:prompts/github-portfolio-analysis.st") Resource analyzePrompt) {
        this.chatClient = chatClient;
        try {
            this.analyzePromptTemplate = analyzePrompt.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load github-portfolio-analysis prompt", e);
        }
    }

    public AiGithubSummary analyzeGithubProject(String repoName, String description,
                                                 String readmeContent, String extraContext,
                                                 List<String> skillCatalog) {
        log.info("PortfolioAiAnalyzer: Analyzing GitHub project: {} against {} catalog skill(s)",
                repoName, skillCatalog != null ? skillCatalog.size() : 0);
        String catalogText = (skillCatalog == null || skillCatalog.isEmpty())
                ? "(empty - return an empty matchedSkills list)"
                : String.join("\n", skillCatalog.stream().map(s -> "- " + s).toList());
        try {
            return chatClient.prompt()
                    .user(String.format(analyzePromptTemplate, catalogText, repoName, description, readmeContent, extraContext))
                    .call()
                    .entity(AiGithubSummary.class);
        } catch (Exception e) {
            log.error("PortfolioAiAnalyzer: AI analysis failed for project: {}", repoName, e);
            return new AiGithubSummary("Project " + repoName + ": " + description, new HashMap<>(), List.of());
        }
    }

    public record SkillMatch(String skill, double confidence) {}

    public record AiGithubSummary(String summary, Map<String, Object> techStack, List<SkillMatch> matchedSkills) {}
}
