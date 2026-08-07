package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * The node a sub-roadmap view is rooted at.
 *
 * <p>The root is deliberately absent from {@code nodes}: drawing it would leave
 * it alone at depth 0 and collapse the whole view into a single column. But
 * dropping it also dropped the only thing that said <em>what kind of place this
 * is</em>, and the client needs that to draw it at all — the nine children of
 * {@code Pick a Language} are nine alternatives, not nine consecutive steps, and
 * without the root there is nothing left in the payload carrying
 * {@code selection = CHOOSE_ONE} to say so. Rendered as a sequence they were
 * numbered 4, 5, 6 and locked behind one another.
 *
 * <p>So the root travels beside the nodes rather than among them: named by the
 * breadcrumb, drawn by nobody, but available to answer "is this view a decision
 * or a path?".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadmapRootResponse {

    private UUID nodeId;

    private String name;

    /** {@code ALL} or {@code CHOOSE_ONE}. */
    private String selection;

    /** How many of the options to take, when the group says so. Usually null. */
    private Integer chooseCount;

    /** {@code CORE}, {@code ALTERNATIVE} or {@code OPTIONAL}. */
    private String nodeKind;

    /**
     * Options at depth 0 of this view. Inside a sub-roadmap every depth-0 node
     * is a direct child of the root, so this is the count the client needs to
     * decide between "one option is not a choice" and a real fork.
     */
    private Integer optionCount;
}
