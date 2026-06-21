package com.inteliroadmap.backend.components;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Component phân tích GitHub repository bằng AI để trích xuất tech stack
 * và tạo mô tả portfolio bằng tiếng Việt.
 */
@Component
@Slf4j
public class PortfolioAiAnalyzer {

    private final ChatClient chatClient;

    public PortfolioAiAnalyzer(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    private static final String ANALYZE_PROMPT = """
            ## TASK
            You are a senior software engineer reviewing a GitHub repository for a student's portfolio.
            Analyze the metadata below and return a structured JSON result.

            ## INSTRUCTIONS
            1. **techStack**: Extract all technologies into a JSON object.
               - Keys MUST be categories: "Frontend", "Backend", "Database", "DevOps", "Testing", "Other".
               - Values are arrays of specific technology names (e.g., ["React", "TypeScript"]).
               - Only include categories that are actually present. Skip empty ones.
               - Be specific: prefer "Spring Boot" over "Java", "PostgreSQL" over "SQL".

            2. **summary**: Write exactly 2 sentences in Vietnamese describing what this project does.
               - Sentence 1: What the project is and its main purpose.
               - Sentence 2: Key technologies used and what makes it notable.
               - Keep it professional and suitable for a portfolio. No filler like "Đây là một dự án...".

            ## INPUT
            - Repo Name: %s
            - Description: %s
            - README snippet:
            %s
            - Config file snippet (package.json / pom.xml / requirements.txt):
            %s

            ## OUTPUT FORMAT (strict JSON, no extra text)
            {
              "summary": "...",
              "techStack": {
                "Backend": ["Spring Boot", "Java"],
                "Database": ["PostgreSQL"]
              }
            }
            """;

    public AiGithubSummary analyzeGithubProject(String repoName, String description,
                                                 String readmeContent, String extraContext) {
        log.info("Analyzing GitHub project: {}", repoName);
        try {
            return chatClient.prompt()
                    .user(String.format(ANALYZE_PROMPT, repoName, description, readmeContent, extraContext))
                    .call()
                    .entity(AiGithubSummary.class);
        } catch (Exception e) {
            log.error("AI analysis failed for project: {}", repoName, e);
            return new AiGithubSummary("Dự án " + repoName + ": " + description, new HashMap<>());
        }
    }

    public record AiGithubSummary(String summary, Map<String, Object> techStack) {}
}
