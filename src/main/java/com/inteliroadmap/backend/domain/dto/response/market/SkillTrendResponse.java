package com.inteliroadmap.backend.domain.dto.response.market;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class SkillTrendResponse {
    private String skillName;
    private List<TrendDataPoint> dataPoints;
}
