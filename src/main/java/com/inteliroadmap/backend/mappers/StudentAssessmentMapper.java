package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.components.SeniorityCalculator;
import com.inteliroadmap.backend.components.SeniorityCalculator.SeniorityVerdict;
import com.inteliroadmap.backend.domain.dto.response.student.AssessedSkillResponse;
import com.inteliroadmap.backend.domain.dto.response.student.AssessmentQuestionResponse;
import com.inteliroadmap.backend.domain.dto.response.student.AssessmentQuestionSetResponse;
import com.inteliroadmap.backend.domain.dto.response.student.StudentAssessmentResultResponse;
import com.inteliroadmap.backend.domain.dto.response.student.StudentLevelResponse;
import com.inteliroadmap.backend.domain.entity.StudentAssessment;
import com.inteliroadmap.backend.domain.enums.ProficiencyLevel;
import com.inteliroadmap.backend.domain.model.AssessmentQuestion;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Shapes assessment rows into the responses the clients read.
 *
 * <p>Also owns the one presentation rule worth stating: the student is always
 * shown both what they claimed and what the grading concluded. A downgrade that
 * happens silently reads as a bug and invites the student to distrust the whole
 * result, so the comparison is part of the contract rather than something each
 * client reconstructs.
 */
@Component
public class StudentAssessmentMapper {

    /**
     * Domain questions to the wire shape. The boundary lives here so
     * {@code AssessmentQuestionBuilder} and the grading logic never import a
     * response DTO.
     */
    public AssessmentQuestionSetResponse toQuestionSetResponse(UUID careerId,
                                                                String careerName,
                                                                List<AssessmentQuestion> questions,
                                                                String notice) {
        return AssessmentQuestionSetResponse.builder()
                .careerId(careerId)
                .careerName(careerName)
                .questions(questions.stream().map(this::toQuestionResponse).toList())
                .notice(notice)
                .build();
    }

    public AssessmentQuestionResponse toQuestionResponse(AssessmentQuestion question) {
        return AssessmentQuestionResponse.builder()
                .skillId(question.skillId())
                .skillName(question.skillName())
                .category(question.category())
                .importance(question.importance() == null ? null : question.importance().name())
                .noteRequired(question.noteRequired())
                .build();
    }

    public StudentAssessmentResultResponse toResultResponse(StudentAssessment assessment,
                                                            String careerName,
                                                            List<AssessedSkillResponse> assessedSkills) {
        return StudentAssessmentResultResponse.builder()
                .assessmentId(assessment.getAssessmentId())
                .careerId(assessment.getCareerId())
                .careerName(careerName)
                .level(assessment.getAiLevel() == null ? null : assessment.getAiLevel().name())
                .rawLevel(assessment.getAiRawLevel() == null ? null : assessment.getAiRawLevel().name())
                .rationale(assessment.getAiRationale())
                .coverage(toDouble(assessment.getRatioAll()))
                .appliedNodeCount(assessment.getAppliedNodeCount() == null ? 0 : assessment.getAppliedNodeCount())
                .assessedSkills(assessedSkills == null ? List.of() : assessedSkills)
                .computedAt(assessment.getComputedAt() == null ? null : assessment.getComputedAt().toString())
                .build();
    }

    public AssessedSkillResponse toAssessedSkillResponse(UUID skillId,
                                                          String skillName,
                                                          ProficiencyLevel declared,
                                                          ProficiencyLevel assessed,
                                                          double confidence,
                                                          String justification) {
        return AssessedSkillResponse.builder()
                .skillId(skillId)
                .skillName(skillName)
                .declaredLevel(declared == null ? null : declared.name())
                .assessedLevel(assessed == null ? null : assessed.name())
                .confidence(confidence)
                .justification(justification)
                .build();
    }

    /**
     * The live level, recomputed rather than read off the stored row — evidence
     * added since the assessment should move it without a re-take. The rationale
     * still comes from the run, because it is the sentence the student was shown
     * and re-wording it every request would make the level look unstable.
     */
    public StudentLevelResponse toLevelResponse(SeniorityVerdict verdict, StudentAssessment source) {
        return StudentLevelResponse.builder()
                .level(verdict.level().name())
                .rationale(source == null ? null : source.getAiRationale())
                .source("ASSESSMENT")
                .coverage(toDouble(verdict.ratioAll()))
                .verifiedCoverage(toDouble(verdict.ratioVerified()))
                .verifiedFloor(SeniorityCalculator.VERIFIED_FLOOR)
                .requiredCount(verdict.requiredCount())
                .heldCount(verdict.heldCount())
                .verifiedCount(verdict.verifiedCount())
                .assessedAt(source == null || source.getComputedAt() == null
                        ? null : source.getComputedAt().toString())
                .build();
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
