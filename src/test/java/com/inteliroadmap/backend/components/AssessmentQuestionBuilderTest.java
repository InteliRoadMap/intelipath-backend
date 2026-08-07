package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
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
class AssessmentQuestionBuilderTest {

    @Mock
    private CareerRequiredSkillRepository careerRequiredSkillRepository;
    @Mock
    private StudentSkillRepository studentSkillRepository;

    private AssessmentQuestionBuilder builder;

    private final UUID careerId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        builder = new AssessmentQuestionBuilder(careerRequiredSkillRepository, studentSkillRepository);
    }

    /**
     * The flow the product is built around: the student types the skills they
     * have, and the assessment checks THOSE. Previously the career's top 15 filled
     * the form and the student's own list got at most five leftover slots.
     */
    @Test
    void everySkillTheStudentDeclaredIsAskedAbout() {
        List<String> declared = List.of("Java", "Spring Boot", "Kafka", "Docker",
                "PostgreSQL", "Redis", "RabbitMQ", "Elasticsearch");
        stubStudentSkills(declared);
        stubCareerSkills(20, ImportanceLevel.HIGH);

        List<String> asked = builder.build(careerId, userId).stream()
                .map(q -> q.skillName())
                .toList();

        declared.forEach(name -> assertTrue(asked.contains(name),
                name + " was declared by the student but never asked about: " + asked));
    }

    /** A declared skill the career grades HIGH must keep HIGH, not be stamped LOW. */
    @Test
    void aDeclaredSkillKeepsTheImportanceTheCareerGivesIt() {
        Skill java = skill("Java");
        when(studentSkillRepository.findByStudent_UserId(any())).thenReturn(List.of(studentSkill(java)));
        when(careerRequiredSkillRepository.findByCareerRole_CareerId(careerId))
                .thenReturn(List.of(required(java, ImportanceLevel.HIGH)));

        var questions = builder.build(careerId, userId);

        assertEquals(1, questions.size());
        assertEquals(ImportanceLevel.HIGH, questions.get(0).importance());
    }

    /** A declared skill the career does not grade is LOW to this role — real information. */
    @Test
    void aDeclaredSkillTheCareerDoesNotGradeStaysLow() {
        Skill unity = skill("Unity");
        Skill java = skill("Java");
        when(studentSkillRepository.findByStudent_UserId(any())).thenReturn(List.of(studentSkill(unity)));
        when(careerRequiredSkillRepository.findByCareerRole_CareerId(careerId))
                .thenReturn(List.of(required(java, ImportanceLevel.HIGH)));

        var unityQuestion = builder.build(careerId, userId).stream()
                .filter(q -> "Unity".equals(q.skillName()))
                .findFirst()
                .orElseThrow();

        assertEquals(ImportanceLevel.LOW, unityQuestion.importance());
    }

    /**
     * LOW rows are the bulk of the table — 384 of Frontend's 504 — so letting them
     * fill the form means asking about trivia while HIGH skills go unasked.
     */
    @Test
    void lowImportanceCareerSkillsNeverFillTheForm() {
        when(studentSkillRepository.findByStudent_UserId(any())).thenReturn(List.of());
        List<CareerRequiredSkill> career = new ArrayList<>();
        career.add(required(skill("Critical Skill"), ImportanceLevel.HIGH));
        for (int i = 0; i < 40; i++) {
            career.add(required(skill("Trivia " + i), ImportanceLevel.LOW));
        }
        when(careerRequiredSkillRepository.findByCareerRole_CareerId(careerId)).thenReturn(career);

        List<String> asked = builder.build(careerId, userId).stream().map(q -> q.skillName()).toList();

        assertTrue(asked.contains("Critical Skill"));
        assertFalse(asked.stream().anyMatch(name -> name.startsWith("Trivia")), asked.toString());
    }

    @Test
    void theFormNeverExceedsItsQuestionCap() {
        stubStudentSkills(java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> "Declared " + i).toList());
        stubCareerSkills(30, ImportanceLevel.HIGH);

        assertEquals(AssessmentQuestionBuilder.MAX_QUESTIONS, builder.build(careerId, userId).size());
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private void stubStudentSkills(List<String> names) {
        when(studentSkillRepository.findByStudent_UserId(any()))
                .thenReturn(names.stream().map(n -> studentSkill(skill(n))).toList());
    }

    private void stubCareerSkills(int count, ImportanceLevel importance) {
        List<CareerRequiredSkill> career = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            career.add(required(skill("Career Skill " + i), importance));
        }
        when(careerRequiredSkillRepository.findByCareerRole_CareerId(careerId)).thenReturn(career);
    }

    private Skill skill(String name) {
        Skill skill = new Skill();
        skill.setSkillId(UUID.randomUUID());
        skill.setSkillName(name);
        return skill;
    }

    private StudentSkill studentSkill(Skill skill) {
        return StudentSkill.builder()
                .student(Student.builder().userId(userId).build())
                .skill(skill)
                .build();
    }

    private CareerRequiredSkill required(Skill skill, ImportanceLevel importance) {
        return CareerRequiredSkill.builder().skill(skill).importanceLevel(importance).build();
    }
}
