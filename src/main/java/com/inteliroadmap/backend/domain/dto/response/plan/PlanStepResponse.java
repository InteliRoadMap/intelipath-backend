package com.inteliroadmap.backend.domain.dto.response.plan;

import com.inteliroadmap.backend.domain.dto.response.market.SkillDemandResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** One thing to learn next, with the evidence for why it is next. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanStepResponse {

    /** 1-based position in the plan. */
    private Integer order;

    private UUID skillId;
    private String skillName;

    /** HIGH | AVG | LOW — how much the target role needs it. */
    private String importance;

    /** Live market figures, or null when the sample is too thin to report. */
    private SkillDemandResponse marketDemand;

    /**
     * Why this step, in one sentence, naming the numbers behind it.
     *
     * <p>The whole point of the plan: a student must be able to disagree with it.
     * "Learn Docker" is not something anyone can argue with; "Docker is required
     * for Backend Developer and appears in 34 of 120 recent postings" is.
     */
    private String why;

    /** Already partly known: 1..4, or null when there is no prior claim at all. */
    private Short currentProficiency;

    /** The nodes that teach it — what the student actually opens and reads. */
    private List<PlanNodeResponse> nodes;
}
