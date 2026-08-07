package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.SubmitGradedAssessmentRequest;
import com.inteliroadmap.backend.domain.dto.response.student.GradedAssessmentPaperResponse;
import com.inteliroadmap.backend.domain.dto.response.student.GradedAssessmentResultResponse;

import java.util.Optional;

/**
 * The graded assessment: a real paper with right answers, for the careers that have
 * a question bank.
 *
 * <p>Sits <b>beside</b> {@link StudentAssessmentService} rather than replacing it.
 * Frontend, Backend and Full Stack have banks in {@code resources/assessment}; the
 * other five careers still use the self-report form, and will until someone writes
 * their papers. The client asks for a paper first and falls back to the old
 * question set when there is none, so adding a bank later is a file, not a release.
 */
public interface GradedAssessmentService {

    /**
     * The paper for the signed-in student's target career, keys stripped.
     *
     * @return empty when that career has no bank — the caller then serves the
     *         self-report questions instead
     */
    Optional<GradedAssessmentPaperResponse> getPaper();

    /**
     * Grade a sat paper, record what it evidences, and set the student's level.
     *
     * <p>The level comes from the paper, not from anything the student claimed about
     * themselves. That is the whole point of the exercise.
     */
    GradedAssessmentResultResponse submit(SubmitGradedAssessmentRequest request);
}
