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

    @Data
    public static class NodePosition {
        @NotNull(message = "Node ID is required")
        private UUID nodeId;

        @NotNull(message = "positionX is required")
        private Double positionX;

        @NotNull(message = "positionY is required")
        private Double positionY;

        private String lane;

        private Integer displayOrder;

        /**
         * Version the client last saw. When present and stale, the save is
         * rejected (409) so a second mentor cannot silently clobber a layout.
         */
        private Integer layoutVersion;
    }
}
