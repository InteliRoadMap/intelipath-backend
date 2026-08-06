package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.components.RoadmapEdge;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapEdgeResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Turns the resolver's edges into the wire shape.
 *
 * <p>Ids become strings here because the node ids already travel as strings on
 * {@code RoadmapNodeResponse} — an edge whose endpoints did not match the node
 * ids it points at would be silently undrawable.
 */
@Component
public class RoadmapEdgeMapper {

    public List<RoadmapEdgeResponse> toResponses(List<RoadmapEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            return List.of();
        }
        return edges.stream()
                .map(edge -> RoadmapEdgeResponse.builder()
                        .source(edge.source().toString())
                        .target(edge.target().toString())
                        .kind(edge.kind())
                        .reason(edge.reason())
                        .build())
                .toList();
    }
}
