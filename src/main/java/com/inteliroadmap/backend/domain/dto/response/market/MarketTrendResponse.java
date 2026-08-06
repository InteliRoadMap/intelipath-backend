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

    /**
     * How current the data behind the other figures actually is.
     *
     * <p>Exists so the UI can state the period rather than implying "now". A chart
     * with no period attached is read as today's market whether or not it is, and
     * that is the difference between a dashboard and a screenshot.
     */
    @Data
    @Builder
    public static class FreshnessResponse {
        private int windowDays;
        /** Distinct jobs advertised in the window. */
        private int jobsInWindow;
        /** Of those, the ones never advertised before — what "new" honestly means. */
        private int newJobs;
        /** Most recent posting date on file, so a stale scrape is visible rather than hidden. */
        private LocalDate latestPostedDate;
    }
}
