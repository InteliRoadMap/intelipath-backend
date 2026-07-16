package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Student's pick inside a CHOOSE_ONE roadmap group, e.g. choosing "Java" within
 * "Pick a Language". Re-sending with a different chosenNodeId switches the choice.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectAlternativeRequest {

    @NotNull(message = "Group node ID is required")
    private UUID groupNodeId;

    @NotNull(message = "Chosen node ID is required")
    private UUID chosenNodeId;
}
