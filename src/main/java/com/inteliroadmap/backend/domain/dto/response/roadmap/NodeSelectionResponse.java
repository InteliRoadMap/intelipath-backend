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

    /**
     * True when the system picked this branch rather than the student.
     *
     * <p>The client has to be able to tell them apart: an auto-pick is a
     * suggestion the student is entitled to overrule, and presenting it as their
     * own past decision removes the invitation to disagree.
     */
    private Boolean autoSelected;

    /** Why it was picked, in the student's own numbers. Null for a manual choice. */
    private String autoReason;
}
