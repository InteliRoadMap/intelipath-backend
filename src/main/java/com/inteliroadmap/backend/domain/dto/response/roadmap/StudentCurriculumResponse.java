package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * The student's FPT-subject checklist. `completedTerm` is inferred from the highest
 * semester among their declared subjects. After a mutation, `recommendations` carries
 * any freshly generated "skip known skills" recommendations so the UI can prompt to
 * apply them; it is null on a plain GET.
 */
@Data
@Builder
public class StudentCurriculumResponse {

    private Integer completedTerm;

    /** The curriculum version the checklist below belongs to (resolved or chosen). */
    private String curriculumId;
    private String curriculumCode;
    private String curriculumLabel;

    /** All curriculum versions the student can pick from (to override the auto-match). */
    private List<CurriculumOptionResponse> availableCurricula;

    private List<FptSubjectResponse> subjects;

    private List<RoadmapRecommendationResponse> recommendations;
}
