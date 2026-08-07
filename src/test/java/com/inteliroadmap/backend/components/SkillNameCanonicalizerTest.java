package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.repositories.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SkillNameCanonicalizerTest {

    @Mock
    private SkillRepository skillRepository;

    private SkillNameCanonicalizer canonicalizer;

    @BeforeEach
    void setUp() {
        canonicalizer = new SkillNameCanonicalizer(skillRepository);
    }

    // ---------- the forks this class exists to close, each measured on the live DB ----------

    @Test
    void fastApiAndFastapiAreOneSkill() {
        assertTrue(canonicalizer.sameSkill("Fast API", "FastAPI"));
        assertTrue(canonicalizer.sameSkill("fastapi", "FastAPI"));
    }

    @Test
    void microServiceSpellingsAreOneSkill() {
        assertTrue(canonicalizer.sameSkill("Micro-service", "Microservices"));
        assertTrue(canonicalizer.sameSkill("Micro Services", "Microservices"));
    }

    @Test
    void theJsSuffixDoesNotRefork() {
        // Removing spaces is what joins "Next.js" to "NextJS"; singularising the second
        // would give "nextj" and split them again. Same shape for React, Vue and Node.
        assertTrue(canonicalizer.sameSkill("Next.js", "NextJS"));
        assertTrue(canonicalizer.sameSkill("React.js", "ReactJS"));
        assertTrue(canonicalizer.sameSkill("Vue.js", "VueJS"));
        assertTrue(canonicalizer.sameSkill("Node.js", "NodeJS"));
    }

    @Test
    void punctuationOnlyDifferencesAreOneSkill() {
        assertTrue(canonicalizer.sameSkill("Elastic Search", "Elasticsearch"));
        assertTrue(canonicalizer.sameSkill("MS SQL", "MSSQL"));
        assertTrue(canonicalizer.sameSkill("Object Oriented Programming", "Object-Oriented Programming"));
        // Roadmap node names arrived with backticks around identifiers: 20 forks from this alone.
        assertTrue(canonicalizer.sameSkill("Using `pg_ctl`", "Using pg_ctl"));
    }

    @Test
    void accentsAndCaseDoNotMakeANewSkill() {
        assertTrue(canonicalizer.sameSkill("JAVA", "java"));
        assertTrue(canonicalizer.sameSkill("Kubernétes", "Kubernetes"));
    }

    // ---------- the merges that must never happen ----------

    @Test
    void golangReachesGoOnlyThroughTheAliasTable() {
        // Golang does reach Go — but by an entry someone wrote down and can argue with,
        // not by a normalisation rule. The proof that no rule is doing it: another name
        // with the same shape, absent from the table, stays separate.
        assertEquals("go", canonicalizer.matchKey("Go"));
        assertEquals("go", canonicalizer.matchKey("Golang"));
        assertFalse(canonicalizer.sameSkill("Go", "Gorilla"));
        assertFalse(canonicalizer.sameSkill("Rust", "Rustlang"));
    }

    @Test
    void cAndCSharpAndCPlusPlusStayThreeSkills() {
        String c = canonicalizer.matchKey("C");
        String cSharp = canonicalizer.matchKey("C#");
        String cPlus = canonicalizer.matchKey("C++");
        assertFalse(c.equals(cSharp));
        assertFalse(c.equals(cPlus));
        assertFalse(cSharp.equals(cPlus));
    }

    @Test
    void shortNamesAreNotSingularised() {
        // "CSS" -> "cs" and "AWS" -> "aw" would invent collisions rather than remove them.
        assertEquals("css3", canonicalizer.matchKey("CSS"));   // via alias, not truncation
        assertEquals("aws", canonicalizer.matchKey("AWS"));
        assertEquals("js", canonicalizer.matchKey("JS"));
    }

    @Test
    void wordsEndingInSsUsOrIsKeepTheirS() {
        assertEquals("express", canonicalizer.matchKey("Express"));
        assertEquals("redis", canonicalizer.matchKey("Redis"));
        assertEquals("nexus", canonicalizer.matchKey("Nexus"));
    }

    @Test
    void anAcronymsTrailingSIsNotAPlural() {
        // Found by running the merge candidates out before writing the migration: naive
        // singularisation folded HTTPS onto HTTP, which are different protocols and would
        // have merged two catalog rows for good.
        assertFalse(canonicalizer.sameSkill("HTTPS", "HTTP"));
        assertFalse(canonicalizer.sameSkill("AWS", "AW"));
        // Same batch of candidates proposed joining MongoDB's $slice to Go's Slices.
        assertFalse(canonicalizer.sameSkill("$slice", "Slices"));
        // Mixed case still means plural: "ORMs" is the plural of "ORM".
        assertTrue(canonicalizer.sameSkill("ORMs", "ORM"));
        assertTrue(canonicalizer.sameSkill("JOINs", "join"));
    }

    @Test
    void differentSkillsStayDifferent() {
        assertFalse(canonicalizer.sameSkill("Java", "JavaScript"));
        assertFalse(canonicalizer.sameSkill("React", "React Native"));
        assertFalse(canonicalizer.sameSkill("Spring", "Spring Boot"));
        assertFalse(canonicalizer.sameSkill("Angular", "AngularJS"));
    }

    // ---------- degenerate input ----------

    @Test
    void nothingIsNotASkill() {
        assertNull(canonicalizer.matchKey(null));
        assertNull(canonicalizer.matchKey(""));
        assertNull(canonicalizer.matchKey("   "));
        assertNull(canonicalizer.matchKey("!!! ---"));
    }

    @Test
    void twoUnnameableStringsAreNotTheSameSkill() {
        assertFalse(canonicalizer.sameSkill("!!!", "???"));
        assertFalse(canonicalizer.sameSkill(null, null));
    }

    // ---------- resolving against the catalog ----------

    @Test
    void resolveFindsTheCatalogRowWhateverDialectAsked() {
        Skill fastapi = skill("FastAPI");
        lenient().when(skillRepository.findAll()).thenReturn(List.of(fastapi, skill("Java")));

        assertEquals(fastapi, canonicalizer.resolve("Fast API"));
        assertEquals(fastapi, canonicalizer.resolve("fastapi"));
    }

    @Test
    void resolvePicksTheSameRowEveryRunWhenTheCatalogHoldsBoth() {
        // Until the merge migration runs, both spellings are still rows. Which one wins
        // must not depend on the order findAll happened to return, or one run's evidence
        // lands on one row and the next run's on the other.
        Skill shortName = skill("FastAPI");
        Skill longName = skill("Fast API");

        lenient().when(skillRepository.findAll()).thenReturn(List.of(longName, shortName));
        assertEquals(shortName, canonicalizer.resolve("fastapi"));

        canonicalizer.invalidate();
        lenient().when(skillRepository.findAll()).thenReturn(List.of(shortName, longName));
        assertEquals(shortName, canonicalizer.resolve("fastapi"));
    }

    @Test
    void resolveFallsBackToTheRepositoryForARowMintedSinceTheIndexWasBuilt() {
        lenient().when(skillRepository.findAll()).thenReturn(List.of(skill("Java")));
        Skill fresh = skill("Bun");
        lenient().when(skillRepository.findOneBySkillNameIgnoreCase("Bun")).thenReturn(fresh);

        assertEquals(fresh, canonicalizer.resolve("Bun"));
    }

    @Test
    void resolveSaysNothingForAnUnnameableName() {
        assertNull(canonicalizer.resolve("   "));
    }

    private static Skill skill(String name) {
        return Skill.builder().skillId(UUID.randomUUID()).skillName(name).build();
    }
}
