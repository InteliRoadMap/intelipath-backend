package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareerAffinityCalculatorTest {

    @Mock
    private StudentSkillRepository studentSkillRepository;
    @Mock
    private CareerRequiredSkillRepository careerRequiredSkillRepository;
    @Mock
    private CareerRoleRepository careerRoleRepository;

    private CareerAffinityCalculator calculator;

    private final UUID userId = UUID.randomUUID();

    private Skill java;
    private Skill spring;
    private Skill sql;
    private Skill react;
    private Skill css;

    private CareerRole backend;
    private CareerRole frontend;

    @BeforeEach
    void setUp() {
        calculator = new CareerAffinityCalculator(
                studentSkillRepository, careerRequiredSkillRepository, careerRoleRepository);

        java = skill("Java");
        spring = skill("Spring Boot");
        sql = skill("SQL");
        react = skill("React");
        css = skill("CSS");

        backend = career("Backend");
        frontend = career("Frontend");
    }

    @Test
    void ranksTheCareerTheStudentOverlapsMostFirst() {
        given(List.of(java, spring),
                required(backend, java, spring, sql),
                required(frontend, react, css));
        when(careerRoleRepository.findAll()).thenReturn(List.of(frontend, backend));

        List<CareerAffinityCalculator.CareerAffinity> ranked = calculator.rank(userId);

        assertEquals("Backend", ranked.get(0).careerName());
        assertEquals(2, ranked.get(0).matched());
        assertEquals(3, ranked.get(0).required());
        // |A ∩ B| = 2, |A ∪ B| = 2 + 3 - 2 = 3, so distance = 1 - 2/3.
        assertEquals(0.333, ranked.get(0).jaccardDistance(), 0.001);
    }

    /** The plan's explicit worry: no skills must not divide by zero. */
    @Test
    void aStudentWithNoSkillsIsAtDistanceOneFromEverythingAndNothingDividesByZero() {
        given(List.of(), required(backend, java, spring), required(frontend, react));
        when(careerRoleRepository.findAll()).thenReturn(List.of(backend, frontend));

        List<CareerAffinityCalculator.CareerAffinity> ranked = calculator.rank(userId);

        assertEquals(2, ranked.size());
        ranked.forEach(affinity -> {
            assertEquals(1.0, affinity.jaccardDistance(), 0.0001);
            assertEquals(0, affinity.matched());
            assertTrue(Double.isFinite(affinity.jaccardDistance()));
        });
        // Stable, alphabetical when everything ties — not a random order per request.
        assertEquals("Backend", ranked.get(0).careerName());
    }

    /**
     * "We have no data for this role" is not "you have nothing in common with
     * it", and only one of those is a statement about the student.
     */
    @Test
    void careersWithNoCoreSkillsAreOmittedRatherThanRankedLast() {
        given(List.of(java), required(backend, java, spring));
        when(careerRoleRepository.findAll()).thenReturn(List.of(backend, frontend));

        List<CareerAffinityCalculator.CareerAffinity> ranked = calculator.rank(userId);

        assertEquals(1, ranked.size());
        assertEquals("Backend", ranked.get(0).careerName());
        assertFalse(ranked.stream().anyMatch(a -> "Frontend".equals(a.careerName())));
    }

    @Test
    void identicalSetsAreAtDistanceZero() {
        given(List.of(java, spring), required(backend, java, spring));
        when(careerRoleRepository.findAll()).thenReturn(List.of(backend));

        assertEquals(0.0, calculator.rank(userId).get(0).jaccardDistance(), 0.0001);
    }

    /** A count nobody can check is a count nobody can argue with. */
    @Test
    void namesTheMatchesBehindTheCount() {
        given(List.of(java, spring), required(backend, java, spring, sql));
        when(careerRoleRepository.findAll()).thenReturn(List.of(backend));

        List<String> named = calculator.rank(userId).get(0).topMatchingSkills();
        assertTrue(named.contains("Java"), named.toString());
        assertTrue(named.contains("Spring Boot"), named.toString());
        assertFalse(named.contains("SQL"), "SQL is not held and must not be listed as a match");
    }

    /**
     * Skills outside the career's core widen the student's set and therefore the
     * union, so holding unrelated things genuinely moves them further away.
     */
    @Test
    void unrelatedSkillsIncreaseTheDistance() {
        given(List.of(java), required(backend, java, spring));
        when(careerRoleRepository.findAll()).thenReturn(List.of(backend));
        double focused = calculator.rank(userId).get(0).jaccardDistance();

        given(List.of(java, react, css), required(backend, java, spring));
        when(careerRoleRepository.findAll()).thenReturn(List.of(backend));
        double scattered = calculator.rank(userId).get(0).jaccardDistance();

        assertTrue(scattered > focused,
                "distance " + scattered + " should exceed " + focused);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    @SafeVarargs
    private void given(List<Skill> mine, List<CareerRequiredSkill>... careerRows) {
        List<StudentSkill> held = new ArrayList<>();
        for (Skill skill : mine) {
            StudentSkill ss = new StudentSkill();
            ss.setSkill(skill);
            held.add(ss);
        }
        when(studentSkillRepository.findByStudent_UserId(userId)).thenReturn(held);

        List<CareerRequiredSkill> all = new ArrayList<>();
        for (List<CareerRequiredSkill> rows : careerRows) {
            all.addAll(rows);
        }
        when(careerRequiredSkillRepository.findByImportanceLevelIn(any())).thenReturn(all);
    }

    private List<CareerRequiredSkill> required(CareerRole career, Skill... skills) {
        List<CareerRequiredSkill> rows = new ArrayList<>();
        for (Skill skill : skills) {
            CareerRequiredSkill row = new CareerRequiredSkill();
            row.setCareerRole(career);
            row.setSkill(skill);
            row.setImportanceLevel(ImportanceLevel.HIGH);
            rows.add(row);
        }
        return rows;
    }

    private Skill skill(String name) {
        Skill skill = new Skill();
        skill.setSkillId(UUID.randomUUID());
        skill.setSkillName(name);
        return skill;
    }

    private CareerRole career(String name) {
        return CareerRole.builder().careerId(UUID.randomUUID()).careerName(name).build();
    }
}
