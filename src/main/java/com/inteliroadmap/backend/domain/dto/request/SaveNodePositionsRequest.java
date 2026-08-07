package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Bulk save of hand-placed node coordinates from the mentor roadmap editor. */
@Data
public class SaveNodePositionsRequest {

    @NotEmpty(message = "At least one node position is required")
    @Valid
    private List<NodePosition> positions;

}
