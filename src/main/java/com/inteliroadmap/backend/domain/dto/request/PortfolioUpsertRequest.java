package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioUpsertRequest {
    @Valid
    private PortfolioConfigRequest config;

    @Valid
    private List<PortfolioProjectRequest> projects;

    @Valid
    private List<StudentEducationRequest> education;

}
