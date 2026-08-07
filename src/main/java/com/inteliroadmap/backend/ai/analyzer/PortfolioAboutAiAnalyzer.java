package com.inteliroadmap.backend.ai.analyzer;

import com.inteliroadmap.backend.domain.dto.response.portfolio.PortfolioAboutDraftResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class PortfolioAboutAiAnalyzer {
    private final ChatClient chatClient;
    private final String promptTemplate;

    public PortfolioAboutAiAnalyzer(ChatClient chatClient,
                                    @Value("classpath:prompts/portfolio-about-draft.st") Resource prompt) {
        this.chatClient = chatClient;
        try {
            this.promptTemplate = prompt.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load portfolio About Me prompt", e);
        }
    }

    public PortfolioAboutDraftResponse draft(String career, String level, String bio,
                                               String primarySkills, String secondarySkills, String projects) {
        try {
            PortfolioAboutDraftResponse draft = chatClient.prompt()
                    .user(String.format(promptTemplate, safe(career), safe(level), safe(bio),
                            safe(primarySkills), safe(secondarySkills), safe(projects)))
                    .call()
                    .entity(PortfolioAboutDraftResponse.class);
            return normalize(draft, career, primarySkills);
        } catch (Exception e) {
            log.error("PortfolioAboutAiAnalyzer: draft generation failed", e);
            throw new IllegalStateException("AI could not generate an About Me draft right now", e);
        }
    }

    private PortfolioAboutDraftResponse normalize(PortfolioAboutDraftResponse draft, String career,
                                                    String primarySkills) {
        if (draft == null) throw new IllegalStateException("AI returned an empty About Me draft");
        String role = normalizeRole(draft.role(), career);
        String description = safe(draft.description()).trim();
        if (description.matches("(?i)^I(?: am|'m)\\b.*")) {
            String topSkills = primarySkills.lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("("))
                    .limit(3)
                    .map(line -> line.replaceFirst("\\s*\\[.*$", ""))
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("my verified skills");
            description = "I build backend systems with " + topSkills + ".";
        }
        return new PortfolioAboutDraftResponse(role, description, safe(draft.objective()).trim());
    }

    private String normalizeRole(String role, String career) {
        String candidate = safe(role).trim();
        if (!candidate.equalsIgnoreCase(safe(career).trim())) return candidate;
        return switch (candidate.toLowerCase()) {
            case "backend" -> "Backend Developer";
            case "frontend" -> "Frontend Developer";
            case "full stack", "fullstack" -> "Full Stack Developer";
            case "data science" -> "Data Scientist";
            case "devops" -> "DevOps Engineer";
            case "qa" -> "QA Engineer";
            default -> candidate;
        };
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "(not provided)" : value;
    }
}
