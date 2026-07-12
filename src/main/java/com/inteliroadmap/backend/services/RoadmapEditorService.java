package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.SaveNodePositionsRequest;
import com.inteliroadmap.backend.domain.dto.request.UpsertRoadmapNodeRequest;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapNodeResponse;

import java.util.List;
import java.util.UUID;

/**
 * Mentor-facing editing of a career's roadmap template: node CRUD, resource
 * links, and hand-placed canvas positions (roadmap.sh-style authoring).
 * Edits apply to the shared template every student of that career sees.
 */
public interface RoadmapEditorService {

    /** All nodes of a career with full editing metadata (no student status). */
    List<RoadmapNodeResponse> getCareerNodes(UUID careerId);

    /** Bulk-saves dragged node coordinates. */
    void saveNodePositions(SaveNodePositionsRequest request);

    RoadmapNodeResponse createNode(UUID careerId, UpsertRoadmapNodeRequest request);

    RoadmapNodeResponse updateNode(UUID nodeId, UpsertRoadmapNodeRequest request);

    /**
     * Deletes a node. Refused while students have progress on it or while
     * other nodes reference it as parent/previous, to avoid silently breaking
     * student data and the graph.
     */
    void deleteNode(UUID nodeId);
}
