package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
import com.inteliroadmap.backend.domain.model.AssessmentQuestion;
import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Assembles the self-assessment for one target career.
 *
 * <p>The questions are built from {@code career_required_skills}, not generated
 * by a model. Three reasons, in order of weight:
 *
 * <ol>
 *   <li>Every answer has to resolve to a real {@code skill_id}. Skill evidence is
 *       discarded when its name has no catalog row, so an invented question would
 *       produce an answer that is silently thrown away — the student would fill in
 *       a form that did nothing.</li>
 *   <li>Grading has to be explainable. If the questions differ per student as well
 *       as the answers, "why was A graded above B" has no answer.</li>
 *   <li>Onboarding is the worst place to wait several seconds for a model.</li>
 * </ol>
 *
 * <p>The model's job is judging the answers, not asking the questions.
 *
 * <p>Returns {@link AssessmentQuestion}, a domain record, rather than a response
 * DTO — this is domain logic and must not depend on the HTTP layer's shape.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssessmentQuestionBuilder {

    /**
     * How many skills to ask about.
     *
     * <p>Fifteen is roughly three minutes of form. Careers here carry hundreds to
     * thousands of required skills (backend alone has ~1,500), so this is a hard
     * cut rather than a safety limit — the top of an importance-sorted list is
     * what a fresher is actually judged on.
     */
    public static final int MAX_QUESTIONS = 15;

    /**
     * Importance grades worth asking about when filling the remaining slots.
     *
     * <p>LOW is excluded: {@code career_required_skills} is the whole catalog
     * scoped by career (Frontend 504 rows, 384 of them LOW), so letting LOW in
     * means asking a student about trivia while the 29 HIGH skills go unasked.
     */
    private static final Set<ImportanceLevel> FILLER_IMPORTANCE =
            Set.of(ImportanceLevel.HIGH, ImportanceLevel.AVG);

    /**
     * How many of the leading skills demand a written justification.
     *
     * <p>Only a few: prose is what lets the grading tell a shipped project from a
     * followed tutorial, but asking for it fifteen times produces fifteen empty
     * boxes. They land on the student's own declarations now, which is where an
     * unverified claim is most worth probing.
     */
    public static final int NOTE_REQUIRED_COUNT = 3;

    private final CareerRequiredSkillRepository careerRequiredSkillRepository;
    private final StudentSkillRepository studentSkillRepository;

    /**
     * @return the questions for {@code careerId}, most important first. Empty when
     *         the career has no required skills on file — the caller reports that
     *         rather than showing a form with nothing in it.
     */
    public List<AssessmentQuestion> build(UUID careerId, UUID userId) {
        List<CareerRequiredSkill> required = careerRequiredSkillRepository.findByCareerRole_CareerId(careerId);
        if (required.isEmpty()) {
            log.warn("AssessmentQuestionBuilder: career {} has no required skills; serving no questions.", careerId);
            return List.of();
        }

        // How the career grades each skill, so a declared skill keeps its real
        // importance instead of being stamped LOW for the crime of being declared.
        Map<UUID, ImportanceLevel> importanceBySkillId = new HashMap<>();
        for (CareerRequiredSkill crs : required) {
            if (crs.getSkill() != null && crs.getSkill().getSkillId() != null) {
                importanceBySkillId.put(crs.getSkill().getSkillId(), crs.getImportanceLevel());
            }
        }

        // The student's own declarations come FIRST and in full. The point of this
        // assessment is to check what they said they know — a student who typed
        // "Java, Spring Boot, Kafka" must be asked about Java, Spring Boot and
        // Kafka. Previously the career's top 15 filled the form and their input
        // was allowed at most five leftover slots, stamped LOW.
        Map<UUID, Draft> picked = new LinkedHashMap<>();
        int declaredCount = 0;
        for (Skill declared : declaredSkills(userId)) {
            if (picked.size() >= MAX_QUESTIONS) break;
            // A declared skill the career does not grade is genuinely LOW to this
            // role. That is real information about the student's direction, not a
            // penalty, and it must not be inflated to the AVG default.
            ImportanceLevel graded = importanceBySkillId.get(declared.getSkillId());
            addSkill(picked, declared, graded != null ? graded : ImportanceLevel.LOW);
            declaredCount++;
        }

        // Whatever room is left goes to the career's core skills, most important
        // first. A stable order matters because the served set is frozen into the
        // assessment row and compared against on submit.
        List<CareerRequiredSkill> sorted = new ArrayList<>(required);
        sorted.sort(Comparator
                .comparingInt((CareerRequiredSkill crs) -> rank(crs.getImportanceLevel()))
                .thenComparing(this::skillNameOf, Comparator.nullsLast(String::compareToIgnoreCase)));
        for (CareerRequiredSkill crs : sorted) {
            if (picked.size() >= MAX_QUESTIONS) break;
            if (!FILLER_IMPORTANCE.contains(crs.getImportanceLevel())) continue;
            addSkill(picked, crs.getSkill(), crs.getImportanceLevel());
        }

        List<Draft> drafts = new ArrayList<>(picked.values());
        List<AssessmentQuestion> questions = new ArrayList<>(drafts.size());
        for (int i = 0; i < drafts.size(); i++) {
            Draft draft = drafts.get(i);
            questions.add(new AssessmentQuestion(
                    draft.skillId(), draft.skillName(), draft.category(), draft.importance(),
                    i < NOTE_REQUIRED_COUNT));
        }

        log.debug("AssessmentQuestionBuilder: built {} questions for career {} ({} from the student's own list).",
                questions.size(), careerId, declaredCount);
        return questions;
    }

    /** A picked skill before its note-required flag is decided by final position. */
    private record Draft(UUID skillId, String skillName, String category, ImportanceLevel importance) {}

    private void addSkill(Map<UUID, Draft> into, Skill skill, ImportanceLevel importance) {
        if (skill == null || skill.getSkillId() == null) return;
        into.putIfAbsent(skill.getSkillId(), new Draft(
                skill.getSkillId(),
                skill.getSkillName(),
                skill.getCategory(),
                importance == null ? ImportanceLevel.AVG : importance));
    }

    private List<Skill> declaredSkills(UUID userId) {
        if (userId == null) return List.of();
        return studentSkillRepository.findByStudent_UserId(userId).stream()
                .map(ss -> ss.getSkill())
                .filter(skill -> skill != null && skill.getSkillId() != null)
                .toList();
    }

    /** HIGH sorts before AVG before LOW; an unset importance sorts with AVG. */
    private int rank(ImportanceLevel importance) {
        if (importance == ImportanceLevel.HIGH) return 0;
        if (importance == ImportanceLevel.LOW) return 2;
        return 1;
    }

    private String skillNameOf(CareerRequiredSkill crs) {
        return crs.getSkill() == null ? null : crs.getSkill().getSkillName();
    }
}
