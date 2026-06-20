package com.inteliroadmap.backend.domain.dto.response.market;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

public class MarketTrendResponse {

    @Data
    @Builder
    public static class CompanyTrendResponse {
        private String topCvCompanyId;
        private String name;
        private String logo;
        private String companyLink;
        private int recruitmentCount;
    }

    @Data
    @Builder
    public static class SkillTrendResponse {
        private String skillName;
        private List<TrendDataPoint> dataPoints;
    }

    @Data
    @Builder
    public static class TrendDataPoint {
        private LocalDate date;
        private int jobsNeeded;
    }

    @Data
    @Builder
    public static class SalaryTrendResponse {
        private String category; // e.g., "0-10 triệu", "10-20 triệu", "20-30 triệu", "30-50 triệu", ">50 triệu"
        private int jobCount;
    }
}
