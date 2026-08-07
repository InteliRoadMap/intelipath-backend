package com.inteliroadmap.backend.domain.dto.response.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * What one repository is currently vouching for on the student's profile.
 *
 * <p>Exists so that deleting a portfolio project can be an informed choice rather than
 * a surprise. The portfolio project and the skill evidence it produced are separate
 * records with separate lifetimes — removing the project from the showcase does not, on
 * its own, retract the claim that the student can write Java. This is the list the
 * student is asked about before that decision is made for them.
 *
 * <p>Only {@code verifying} skills are worth stopping the student for. The others are
 * shown so the account is complete, but a REJECTED row lost to a stronger claim long
 * ago and withdrawing it costs nothing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepoEvidenceResponse {

    private String repoUrl;

    /**
     * How many of {@link #skills} are ACCEPTED — the ones actually holding the level up,
     * and the number the confirmation dialog should lead with.
     */
    private int verifyingCount;

    private List<EvidenceSkillResponse> skills;

}
