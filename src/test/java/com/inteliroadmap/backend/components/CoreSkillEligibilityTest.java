package com.inteliroadmap.backend.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreSkillEligibilityTest {

    private final CoreSkillEligibility eligibility = new CoreSkillEligibility();

    // ---------- rule 1: a name that lists several things is not one skill ----------

    @Test
    void compoundNamesAreNotCoreSkills() {
        assertFalse(eligibility.isCoreEligible("Cloud Computing & AWS"));
        assertFalse(eligibility.isCoreEligible("Java / Kotlin / Scala / Swift"));
        assertFalse(eligibility.isCoreEligible("Testing Methodologies & Techniques"));
        assertFalse(eligibility.isCoreEligible("Git and Version Control"));
        assertFalse(eligibility.isCoreEligible("JavaScript / TypeScript"));
    }

    @Test
    void aSeparatorInsideANameIsNotAList() {
        // The whitespace is the signal, not the character. Without that distinction the
        // rule would delete three of the best-attested skills in the corpus.
        assertTrue(eligibility.isCoreEligible("CI/CD"));
        assertTrue(eligibility.isCoreEligible("TCP/IP"));
        assertTrue(eligibility.isCoreEligible("C++"));
        assertTrue(eligibility.isCoreEligible("Node.js"));
    }

    // ---------- rule 2: chapter titles are measured through their contents ----------

    @Test
    void headingSuffixesAreNotCoreSkills() {
        assertFalse(eligibility.isCoreEligible("Software Architecture Fundamentals"));
        assertFalse(eligibility.isCoreEligible("QA Fundamentals"));
        assertFalse(eligibility.isCoreEligible("Architecture Styles"));
        assertFalse(eligibility.isCoreEligible("Code Quality Tools"));
        assertFalse(eligibility.isCoreEligible("Linux Basics"));
    }

    @Test
    void aHeadingWordElsewhereInTheNameIsHarmless() {
        // "Design Patterns" is a heading; "Pattern Matching" is a skill. A substring test
        // would lose the second to catch the first.
        assertTrue(eligibility.isCoreEligible("Pattern Matching"));
        assertTrue(eligibility.isCoreEligible("Design Thinking"));
        assertTrue(eligibility.isCoreEligible("Tools4ever"));
    }

    // ---------- rule 3: a category is not a thing to learn ----------

    @Test
    void categoryWordsAreNotCoreSkills() {
        assertFalse(eligibility.isCoreEligible("Cloud"));
        assertFalse(eligibility.isCoreEligible("API"));
        assertFalse(eligibility.isCoreEligible("Database"));
        assertFalse(eligibility.isCoreEligible("Software Development"));
        assertFalse(eligibility.isCoreEligible("Automation"));
        assertFalse(eligibility.isCoreEligible("devops"));
    }

    @Test
    void aCategoryWordInsideAProductNameIsHarmless() {
        assertTrue(eligibility.isCoreEligible("Cloud Firestore"));
        assertTrue(eligibility.isCoreEligible("API Gateway"));
        assertTrue(eligibility.isCoreEligible("Database Migration"));
    }

    // ---------- the rules must not sweep up real skills ----------

    @Test
    void realSkillsSurvive() {
        // Everything here is a single, specific, nameable thing. Several of them have
        // zero Vietnamese postings - that is CareerSkillMarketGrader's question, asked
        // separately and answered from data, not this one.
        for (String name : new String[]{
                "Spring Boot", "PostgreSQL", "React", "TypeScript", "Docker", "Kubernetes",
                "TDD", "bcrypt", "OpenGL", "Vulkan", "JMeter", "Cypress", "Unreal Engine",
                "GitOps", "Caching", "Salesforce", "REST", "GraphQL", "Redis"}) {
            assertTrue(eligibility.isCoreEligible(name), name + " should be core-eligible");
        }
    }

    /**
     * The rows that reached the student's skill picker. Every one of these is a real
     * catalog row today — imported roadmap node titles that are literally source code —
     * and the picker offered them for a student to declare, sorted above {@code Android}.
     */
    @Test
    void codeFragmentsAreNotSkillNames() {
        for (String name : new String[]{
                "$match", "$elemMatch", "$unwind", "$and", "@if", "@else if",
                "@Input & @Output", "@SpringBootTest Annotation", "[global] keyword",
                "--watch", "-replace option in apply"}) {
            assertFalse(eligibility.isCoreEligible(name), name + " is code, not a skill name");
        }
    }

    /**
     * Twenty catalog rows are phrased as questions, all of them imported roadmap node
     * titles — and one of them, {@code What is a Domain Name?}, had been declared by a
     * student as something they can do.
     */
    @Test
    void aQuestionIsALessonTitleNotASkill() {
        assertFalse(eligibility.isCoreEligible("What is a Domain Name?"));
        assertFalse(eligibility.isCoreEligible("What are Data Structures?"));
        assertFalse(eligibility.isCoreEligible("How RDB Works?"));
        assertTrue(eligibility.isCoreEligible("Data Structures"), "the statement form is fine");
    }

    /**
     * The rule is about the first character, so it must not swallow the real names that
     * merely contain a symbol — nor {@code .NET}, whose leading dot IS the name.
     */
    @Test
    void aSymbolInsideOrALeadingDotIsHarmless() {
        assertTrue(eligibility.isCoreEligible(".NET"));
        assertTrue(eligibility.isCoreEligible(".NET Core"));
        assertTrue(eligibility.isCoreEligible("C#"));
        assertTrue(eligibility.isCoreEligible("C++"));
        assertTrue(eligibility.isCoreEligible("Node.js"));
        assertTrue(eligibility.isCoreEligible("CI/CD"));
        assertTrue(eligibility.isCoreEligible("3D Modeling"));
    }

    // ---------- degenerate input ----------

    @Test
    void nothingIsNotACoreSkill() {
        assertFalse(eligibility.isCoreEligible(null));
        assertFalse(eligibility.isCoreEligible(""));
        assertFalse(eligibility.isCoreEligible("   "));
    }

    @Test
    void aSentenceIsNotASkillName() {
        assertFalse(eligibility.isCoreEligible(
                "Understanding how distributed systems reach consensus"));
    }

    @Test
    void mintingAsksTheSameQuestion() {
        // One answer, two consequences: a name that could never be graded HIGH is not
        // worth minting a catalog row for either.
        assertFalse(eligibility.isNameable("Software Development"));
        assertTrue(eligibility.isNameable("FastAPI"));
    }
}
