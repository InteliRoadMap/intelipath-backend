package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A standalone roadmap merged under a career — a language, a framework, a
 * database track — offered as somewhere to go rather than a step to walk past.
 *
 * <p>These used to sit on the career path as ordinary roots. Because they carry
 * {@code node_level = 0} they sorted ahead of every real step, so Backend opened
 * on ASP.NET Core, Golang and Scala before it reached "Internet" — 400+ nodes of
 * languages the student had not chosen, in alphabetical order.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubRoadmapResponse {

    private UUID nodeId;
    private String name;
    private String description;

    /** Nodes inside it, so the card can say how much of a commitment it is. */
    private Integer nodeCount;

    /** How many of those the student has finished. */
    private Integer completedCount;

    /**
     * True when this is the track the student is following, in which case it is
     * shown on the career path itself rather than only behind a click.
     */
    private Boolean chosen;
}
