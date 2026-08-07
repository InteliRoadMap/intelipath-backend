package com.inteliroadmap.backend.domain.dto.response.student;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One selectable option. The key is what the client sends back. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentChoiceResponse {
    private String key;
    private String text;
}
