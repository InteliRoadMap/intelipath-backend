package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Whether the FPT curriculum teaches a roadmap node's skill. {@code covered} means at
 * least one FPT subject teaches it (listed in {@code subjects}); {@code selfStudy} flags
 * a concrete skill the curriculum does not cover, so the student must learn it on their own.
 */
@Data
@Builder
public class FptNodeCoverageResponse {

    private boolean covered;
    private boolean selfStudy;
    private List<FptSubjectRefResponse> subjects;

}
