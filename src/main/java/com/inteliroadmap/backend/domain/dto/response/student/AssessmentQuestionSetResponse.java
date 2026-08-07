package com.inteliroadmap.backend.domain.dto.response.student;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** The questions served for one assessment run. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentQuestionSetResponse {

    private UUID careerId;
    private String careerName;

    @Builder.Default
    private List<AssessmentQuestionResponse> questions = List.of();

    /**
     * Set when the target career has no required skills on file, in which case
     * {@link #questions} is empty. The client shows this instead of an empty
     * form; the student is not asked to fill in nothing.
     */
    private String notice;
}
