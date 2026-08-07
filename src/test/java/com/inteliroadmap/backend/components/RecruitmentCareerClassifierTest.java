package com.inteliroadmap.backend.components;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This classification decides which skills count as demand for which career, so a
 * wrong answer is not a missing row — it is a false one. The tests weighted most
 * heavily are the ones about declining to answer and about compound titles.
 */
class RecruitmentCareerClassifierTest {

    private final RecruitmentCareerClassifier classifier = new RecruitmentCareerClassifier();

    @Test
    @DisplayName("plain specialisations resolve")
    void plainTitlesResolve() {
        assertThat(classifier.classify("Backend Developer (Java, Spring Boot)")).isEqualTo("Backend");
        assertThat(classifier.classify("Front-end Developer ReactJS")).isEqualTo("Frontend");
        assertThat(classifier.classify("DevOps Engineer")).isEqualTo("DevOps");
        assertThat(classifier.classify("Data Engineer (Python)")).isEqualTo("Data Science");
        assertThat(classifier.classify("Manual Tester")).isEqualTo("QA");
        assertThat(classifier.classify("Unity Game Developer")).isEqualTo("Game Developer");
    }

    @Test
    @DisplayName("a fullstack title is Full Stack, not the first stack it names")
    void compoundTitlesBeatSingleStackKeywords() {
        // "Fullstack Java Developer" contains "java"; tested in the wrong order this
        // becomes a Backend posting and Full Stack loses evidence it should have had.
        assertThat(classifier.classify("Fullstack Java Developer")).isEqualTo("Full Stack");
        assertThat(classifier.classify("Full-stack Engineer (React/Node.js)")).isEqualTo("Full Stack");
    }

    @Test
    @DisplayName("an architect is an architect, whatever technology is in brackets")
    void roleOutranksTechnology() {
        assertThat(classifier.classify("Solution Architect (Java)")).isEqualTo("Software Architect");
        assertThat(classifier.classify("Technical Lead - .NET")).isEqualTo("Software Architect");
    }

    @Test
    @DisplayName("a title naming no specialisation is declined, not guessed")
    void unspecificTitlesReturnNull() {
        // The whole reason null is a supported answer: forcing these into the nearest
        // career would make them evidence for skills that role never asked for.
        assertThat(classifier.classify("Software Engineer")).isNull();
        assertThat(classifier.classify("IT Staff")).isNull();
        assertThat(classifier.classify("Lập trình viên")).isNull();
        assertThat(classifier.classify("Internship Program 2026")).isNull();
    }

    @Test
    @DisplayName("word boundaries stop the short keywords matching inside other words")
    void shortKeywordsDoNotMatchInsideWords() {
        assertThat(classifier.classify("Maintenance Engineer")).isNull();       // 'ai' inside "maintenance"
        assertThat(classifier.classify("Sales Executive - Qatar Branch")).isNull(); // 'qa' inside "qatar"
        assertThat(classifier.classify("JavaScript Developer")).isNotEqualTo("Backend");
    }

    @Test
    @DisplayName("JavaScript is not Java")
    void javaScriptIsNotBackendByTheJavaKeyword() {
        // \bjava\b(?!script) — without the lookahead every JavaScript posting becomes
        // Backend demand, which is exactly the kind of quiet corruption this table is
        // meant to remove rather than introduce.
        assertThat(classifier.classify("Senior JavaScript Engineer")).isNull();
    }

    @Test
    @DisplayName("nothing in, nothing out")
    void emptyInputIsDeclined() {
        assertThat(classifier.classify(null)).isNull();
        assertThat(classifier.classify("   ")).isNull();
    }
}
