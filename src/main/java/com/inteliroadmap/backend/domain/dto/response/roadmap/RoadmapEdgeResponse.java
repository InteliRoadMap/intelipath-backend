package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One connection on the student's roadmap, as drawn.
 *
 * <p>Edges used to be implicit — the frontend rebuilt them from each node's
 * {@code parentNode} / {@code previousNode}, which meant every student got the
 * same graph. They are sent explicitly now because they are computed per student
 * and carry a {@link #reason} the student can read.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadmapEdgeResponse {

    private String source;

    private String target;

    /** SEQUENCE — learn this first; HIERARCHY — this belongs to that topic. */
    private String kind;

    /** Why this ordering, in one sentence, shown on hover. */
    private String reason;
}
