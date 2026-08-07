package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A student's reply to a feedback message they received. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReplyFeedbackRequest {

    @NotBlank(message = "Reply content must not be empty")
    @Size(max = 5000, message = "Reply must be at most 5000 characters")
    private String content;
}
