package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** One answer to one graded item. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentItemAnswerRequest {

    @NotBlank(message = "itemId is required")
    private String itemId;

    /** Selected option keys. Empty or absent counts as unanswered, which scores zero. */
    private List<String> choiceKeys;

    /**
     * Prose or code, for the rubric-graded kinds.
     *
     * <p>Capped well above a reasonable answer rather than at one: the cap exists so
     * a paste of an entire repository cannot reach the model, not to cut anyone off
     * mid-thought. The grader truncates again at its own limit.
     */
    @Size(max = 20000, message = "answer must be at most 20000 characters")
    private String text;
}
