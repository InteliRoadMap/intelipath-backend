package com.inteliroadmap.backend.domain.dto.response.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** The real source files selected for one repository analysis run. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepoSourcePlanResponse {
    private String repoUrl;
    private String repoFullName;
    private List<String> sourcePaths;
}
