package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Student's explicit choice of which FLM curriculum version they follow. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetStudentCurriculumRequest {

    @NotNull(message = "A curriculum id is required")
    private UUID curriculumId;
}
