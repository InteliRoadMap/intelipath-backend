package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Manual per-subject tick/untick. Each entry sets whether the student has passed
 * that FPT subject; passed=true upserts a MANUAL PASSED row, passed=false removes
 * the student's record for that subject.
 */
@Data
public class UpdateFptSubjectsRequest {

    @NotNull
    @Valid
    private List<SubjectEntry> subjects;

}
