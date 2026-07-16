package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One stored CHOOSE_ONE decision of the current student: which alternative was
 * picked inside which group node.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeSelectionResponse {
    private UUID groupNodeId;
    private String groupNodeName;
    private UUID chosenNodeId;
    private String chosenNodeName;
    private LocalDateTime createdAt;
}
