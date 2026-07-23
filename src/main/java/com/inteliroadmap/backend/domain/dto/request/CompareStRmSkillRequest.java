package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompareStRmSkillRequest {
    @NotNull(message = "Career ID is required")
    private UUID careerId;
}
