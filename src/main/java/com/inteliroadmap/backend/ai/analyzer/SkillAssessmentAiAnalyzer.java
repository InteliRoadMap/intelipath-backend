package com.inteliroadmap.backend.ai.analyzer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Grades a student's career self-assessment.
 *
 * <p>The model is an adjudicator, not an examiner: the questions are assembled
 * deterministically elsewhere, and what arrives here is a set of claims plus the
 * student's own written justification for the strongest of them. The model's job
 * is to decide which claims the prose actually supports — "I followed a YouTube
 * tutorial" is not the PROFESSIONAL the student ticked.
 */
@Component
@Slf4j
public class SkillAssessmentAiAnalyzer {

    private final ChatClient chatClient;
    private final String evaluationPromptTemplate;

    public SkillAssessmentAiAnalyzer(ChatClient chatClient,
                                     @Value("classpath:prompts/skill-assessment-evaluation.st") Resource evaluationPrompt) {
        this.chatClient = chatClient;
        try {
            this.evaluationPromptTemplate = evaluationPrompt.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load skill-assessment-evaluation prompt", e);
        }
    }

    /**
     * @param careerName      the target role, e.g. "Backend Developer"
     * @param askedSkillNames the skills this run asked about — and the only names
     *                        the model is allowed to return. Deliberately not the
     *                        career's whole catalog: that runs to ~1,500 names for
     *                        backend, and the answers can only mention what was
     *                        asked, so the extra names cost tokens and buy nothing.
     * @param answersText     pre-rendered "- React: APPLIED — <note>" lines
     */
    public AiAssessmentVerdict evaluate(String careerName, List<String> askedSkillNames, String answersText) {
        log.info("SkillAssessmentAiAnalyzer: grading a self-assessment for {} across {} skill(s)",
                careerName, askedSkillNames == null ? 0 : askedSkillNames.size());

        String skillListText = (askedSkillNames == null || askedSkillNames.isEmpty())
                ? "(empty - return an empty assessedSkills list)"
                : String.join("\n", askedSkillNames.stream().map(s -> "- " + s).toList());

        try {
            return chatClient.prompt()
                    .user(String.format(evaluationPromptTemplate, careerName, skillListText, careerName, answersText))
                    .call()
                    .entity(AiAssessmentVerdict.class);
        } catch (Exception e) {
            // Onboarding must not fail because the model did. An empty skill list
            // tells the caller to fall back to the student's own declared levels
            // at self-report confidence — the same treatment the manual skill
            // screen has always given them, so the student loses the fast-track,
            // not the account.
            log.error("SkillAssessmentAiAnalyzer: AI evaluation failed for career: {}", careerName, e);
            return new AiAssessmentVerdict(
                    "FRESHER",
                    "Automatic evaluation was unavailable, so your own answers were used as given. "
                            + "You can re-take the assessment at any time from your dashboard.",
                    0.0,
                    List.of());
        }
    }

    /**
     * @param skill         a name copied verbatim from the list that was sent
     * @param level         1 AWARE, 2 PRACTICED, 3 APPLIED, 4 PROFESSIONAL
     * @param confidence    0..1, how sure the model is
     * @param justification one sentence explaining the level
     */
    public record AssessedSkill(String skill, int level, double confidence, String justification) {}

    /** @param level FRESHER | JUNIOR | MID, before the deterministic ceiling is applied. */
    public record AiAssessmentVerdict(String level,
                                      String rationale,
                                      double overallConfidence,
                                      List<AssessedSkill> assessedSkills) {}
}
