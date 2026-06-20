package com.inteliroadmap.backend.components;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioAiAnalyzer {

    private final ChatClient.Builder chatClientBuilder;

    public AiGithubSummary analyzeGithubProject(String repoName, String description, String readmeContent, String extraContext) {
        String aiPrompt = """
            Read the following GitHub repository metadata and files.
            Extract the tech stack used in the project as a JSON object where keys are categories (e.g., 'Frontend', 'Backend', 'Database', 'Tooling') and values are arrays of strings.
            Also provide a concise 2-sentence summary in Vietnamese of what the project does for a personal portfolio.
            
            Repo Name: %s
            Description: %s
            
            README.md snippet:
            %s
            
            Extra Configuration file snippet (e.g. package.json, pom.xml):
            %s
            """;
            
        try {
            ChatClient chatClient = chatClientBuilder.build();
            return chatClient.prompt()
                    .user(String.format(aiPrompt, repoName, description, readmeContent, extraContext))
                    .call()
                    .entity(AiGithubSummary.class);
        } catch (Exception e) {
            log.error("AI summarization failed for project {}", repoName, e);
            return new AiGithubSummary(description, new HashMap<>());
        }
    }

    public record AiGithubSummary(String summary, Map<String, Object> techStack) {}
}
