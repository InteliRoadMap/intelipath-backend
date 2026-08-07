package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * A CHOOSE_ONE group's options, ranked.
 *
 * <p>{@code verdict} is the honest part. {@code DECISIVE} means the top option
 * is clear enough to recommend; {@code TOO_CLOSE} means two are within ten
 * percent of each other and the scorer refuses to break the tie;
 * {@code NO_SIGNAL} means the student holds nothing that appears in any branch.
 * The client must render the last two as themselves rather than promoting
 * {@code options.get(0)} anyway — a recommendation with nothing behind it is
 * worse than no recommendation, because the student cannot tell them apart.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChoiceOptionsResponse {

    private UUID groupNodeId;

    private String groupName;

    /** {@code DECISIVE}, {@code TOO_CLOSE} or {@code NO_SIGNAL}. */
    private String verdict;

    /** Best fit first; ties broken by market frequency, then by name. */
    private List<ChoiceOptionResponse> options;
}
