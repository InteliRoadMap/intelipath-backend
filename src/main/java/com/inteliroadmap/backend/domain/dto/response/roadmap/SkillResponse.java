package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;
import com.inteliroadmap.backend.domain.dto.response.market.MarketSkillGapResponse;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SkillResponse {
    @Builder.Default
    private List<SkillItemResponse> selectedSkills = List.of();

    @Builder.Default
    private List<SkillItemResponse> skills = List.of();

    @Builder.Default
    private List<RequiredSkillResponse> requiredSkills = List.of();

    @Builder.Default
    private List<SkillItemResponse> missingSkills = List.of();

    @Builder.Default
    private List<CareerSkillGapResponse> careerSkillGaps = List.of();

    @Builder.Default
    private List<MarketSkillGapResponse> marketSkillGaps = List.of();

    /**
     * Roadmap nodes the declaration just marked as already covered.
     *
     * <p>Only ever filled by the import endpoint, and empty on every read — the
     * ids are a receipt for something that just happened, not a property of the
     * student's skill list.
     */
    @Builder.Default
    private List<UUID> markedNodeIds = List.of();
}
