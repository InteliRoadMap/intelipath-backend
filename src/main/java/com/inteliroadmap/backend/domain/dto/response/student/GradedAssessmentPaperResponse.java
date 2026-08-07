package com.inteliroadmap.backend.domain.dto.response.student;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** The graded paper served for one career, question keys stripped. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradedAssessmentPaperResponse {
    private UUID careerId;
    private String careerName;
    /** BACKEND | FRONTEND | FULLSTACK. */
    private String scope;
    private int version;
    @Builder.Default
    private List<AssessmentItemResponse> items = List.of();
}
