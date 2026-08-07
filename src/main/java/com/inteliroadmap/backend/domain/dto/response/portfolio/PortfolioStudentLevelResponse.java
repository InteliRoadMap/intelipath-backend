package com.inteliroadmap.backend.domain.dto.response.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Public, read-only career level shown on a student's portfolio. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioStudentLevelResponse {
    private String level;
    private String source;
    private String assessedAt;
}
