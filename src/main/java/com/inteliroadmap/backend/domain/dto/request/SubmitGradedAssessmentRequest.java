package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A sat paper.
 *
 * <p>Separate from {@code SubmitAssessmentRequest} rather than an extension of it:
 * the self-report form still exists for the five careers with no question bank, and
 * one request type carrying both shapes would make every field optional and every
 * validation rule conditional.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitGradedAssessmentRequest {

    @NotEmpty(message = "answers must not be empty")
    @Valid
    private List<AssessmentItemAnswerRequest> answers;
}
