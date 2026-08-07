package com.inteliroadmap.backend.domain.dto.response.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceSkillResponse {
    private String skill;
    /** ACCEPTED | PENDING | REJECTED, as the evidence row stands right now. */
    private String status;
}
