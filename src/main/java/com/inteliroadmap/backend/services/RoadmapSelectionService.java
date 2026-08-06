package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.SelectAlternativeRequest;
import com.inteliroadmap.backend.domain.dto.response.roadmap.ChoiceOptionsResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.NodeSelectionResponse;

import java.util.List;
import java.util.UUID;

/**
 * Manages the current student's picks inside CHOOSE_ONE roadmap groups (e.g.
 * choosing Java within "Pick a Language"). The roadmap tree stays a shared
 * template; these selections are the per-student overlay that decides which
 * alternative branch is active.
 */
public interface RoadmapSelectionService {

    /** Picks (or switches to) an alternative inside a CHOOSE_ONE group. */
    NodeSelectionResponse selectAlternative(SelectAlternativeRequest request);

    /** All stored selections of the current student. */
    List<NodeSelectionResponse> getSelections();

    /** Removes the selection for one group, returning the group to "not chosen yet". */
    void clearSelection(UUID groupNodeId);

    /**
     * The group's options ranked by fit, with the market figures beside them.
     *
     * <p>Read-only: opening the chooser must never store a choice. Ranked by the
     * same scorer {@link #autoDefaultSelections()} uses, so the option presented
     * as the best fit is the one the system would have taken.
     */
    ChoiceOptionsResponse getOptions(UUID groupNodeId);

    /**
     * Pre-selects alternatives from the student's skill profile: for every
     * CHOOSE_ONE group without a stored selection, when exactly one alternative
     * matches a student_skills entry (by node name or evidence keyword, e.g.
     * skill "Java" -> node "Java") that alternative is selected automatically.
     * Ambiguous or zero matches leave the group unchosen.
     *
     * @return number of selections created
     */
    int autoDefaultSelections();
}
