package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * "I have finished up to term N of the FPT curriculum." Every subject with
 * semester &lt;= completedTerm is marked PASSED (source CURRICULUM_TERM).
 */
@Data
public class DeclareCurriculumTermRequest {

    @NotNull
    @Min(0)
    @Max(9)
    private Integer completedTerm;
}
