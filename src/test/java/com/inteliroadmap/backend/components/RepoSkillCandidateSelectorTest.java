package com.inteliroadmap.backend.components;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shortlist decides what the model is even able to say. A skill left out of it
 * cannot be returned no matter how plainly the repository demonstrates it, so these
 * tests care most about what gets in and what wrongly gets in.
 */
class RepoSkillCandidateSelectorTest {

    private final RepoSkillCandidateSelector selector = new RepoSkillCandidateSelector();

    /** Ordered by market demand, the way the repository query returns them. */
    private static final List<String> CAREER_SKILLS = List.of(
            "Java", "Docker", "PostgreSQL", "Spring Boot", "REST", "Agile", "Microservices",
            "Arithmetic", "awk", "Basic Syntax", "Comments", "Hibernate", "JWT", "Maven", "Redis");

    @Test
    @DisplayName("a skill the repository names beats a higher-demand one it never mentions")
    void repositoryEvidenceOutranksMarketDemand() {
        // The measured failure: Maven sat at rank 881 of 1434 and never reached the model,
        // so a Spring Boot repository built with Maven could not have it recognised.
        List<String> chosen = selector.select(CAREER_SKILLS,
                List.of("<artifactId>maven-compiler-plugin</artifactId>", "Add JWT refresh rotation"),
                Map.of("Java", 1_159_265L), 5);

        assertThat(chosen).contains("Maven", "JWT", "Java");
        assertThat(chosen).doesNotContain("Arithmetic", "awk");
    }

    @Test
    @DisplayName("leftover slots still go to the most in-demand skills")
    void remainingSlotsFallBackToDemand() {
        List<String> chosen = selector.select(CAREER_SKILLS, List.of("maven"), Map.of(), 4);

        assertThat(chosen).hasSize(4);
        assertThat(chosen.get(0)).isEqualTo("Maven");
        // Then the demand order resumes from the top.
        assertThat(chosen.subList(1, 4)).containsExactly("Java", "Docker", "PostgreSQL");
    }

    @Test
    @DisplayName("Java does not match because the text says JavaScript")
    void wordBoundariesStopSubstringFalsePositives() {
        List<String> chosen = selector.select(List.of("Java", "Redis"),
                List.of("built with JavaScript and TypeScript"), Map.of(), 1);

        // Only the demand fill should have put anything here; Java must not have been
        // selected as if the repository demonstrated it.
        assertThat(chosen).hasSize(1);
        assertThat(chosen.get(0)).isEqualTo("Java"); // by demand, not by a false match
    }

    @Test
    @DisplayName("a two-letter name is never matched by text — 'Go' is inside 'Google'")
    void shortNamesAreNotTextMatched() {
        List<String> chosen = selector.select(List.of("Redis", "Go"),
                List.of("deployed on Google Cloud"), Map.of(), 1);

        assertThat(chosen).containsExactly("Redis");
    }

    @Test
    @DisplayName("dots and hashes are part of a name, not boundaries")
    void punctuatedNamesMatch() {
        List<String> chosen = selector.select(List.of("Node.js", "C#", "Java"),
                List.of("runtime: Node.js 20"), Map.of(), 3);

        assertThat(chosen.get(0)).isEqualTo("Node.js");
    }

    @Test
    @DisplayName("GitHub's language names count as repository evidence on their own")
    void languageNamesAreSearched() {
        List<String> chosen = selector.select(List.of("Java", "Docker", "Redis"),
                List.of(), Map.of("Redis", 4_000L), 2);

        assertThat(chosen.get(0)).isEqualTo("Redis");
    }

    @Test
    @DisplayName("a repository that says nothing is no worse off than before")
    void noSignalsFallsBackEntirelyToDemand() {
        List<String> chosen = selector.select(CAREER_SKILLS, List.of(), Map.of(), 3);
        assertThat(chosen).containsExactly("Java", "Docker", "PostgreSQL");
    }

    @Test
    @DisplayName("the shortlist never exceeds the room the prompt has")
    void limitIsRespected() {
        assertThat(selector.select(CAREER_SKILLS, List.of("java docker postgresql maven jwt"),
                Map.of(), 2)).hasSize(2);
        assertThat(selector.select(CAREER_SKILLS, List.of("java"), Map.of(), 0)).isEmpty();
        assertThat(selector.select(List.of(), List.of("java"), Map.of(), 10)).isEmpty();
    }

    @Test
    @DisplayName("a skill is never listed twice")
    void noDuplicates() {
        List<String> chosen = selector.select(CAREER_SKILLS, List.of("java java java"), Map.of(), 5);
        assertThat(chosen).doesNotHaveDuplicates();
    }
}
