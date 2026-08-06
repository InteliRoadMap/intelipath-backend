package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.SubmitAssessmentRequest;
import com.inteliroadmap.backend.domain.dto.response.student.AssessmentQuestionSetResponse;
import com.inteliroadmap.backend.domain.dto.response.student.StudentAssessmentResultResponse;

import java.util.Optional;

/**
 * The optional career self-assessment offered when a student sets up their
 * account.
 *
 * <p>Optional is load-bearing: a student who skips it must get the same working
 * application as before this existed. Nothing here may become a precondition for
 * the roadmap, the market view or the mentor.
 */
public interface StudentAssessmentService {

    /**
     * The question set for the authenticated student's target career, matched to
     * the skills that career actually requires.
     */
    AssessmentQuestionSetResponse getQuestionSet();

    /**
     * Grades a submitted assessment, records the resulting evidence, and marks
     * the roadmap nodes the student has demonstrably covered.
     */
    StudentAssessmentResultResponse submitAssessment(SubmitAssessmentRequest request);

    /**
     * The student's most recent completed run, or empty when they have never
     * taken it — which is a normal state, not an error.
     */
    Optional<StudentAssessmentResultResponse> getLatestResult();
}
