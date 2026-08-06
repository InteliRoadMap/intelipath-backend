package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** A completed self-assessment: one answer per question that was served. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitAssessmentRequest {

    @NotEmpty(message = "answers must not be empty")
    @Valid
    private List<AssessmentAnswerRequest> answers;
}
