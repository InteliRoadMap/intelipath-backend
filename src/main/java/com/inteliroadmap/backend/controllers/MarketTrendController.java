package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.response.market.CompanyTrendResponse;
import com.inteliroadmap.backend.domain.dto.response.market.FreshnessResponse;
import com.inteliroadmap.backend.domain.dto.response.market.SalaryTrendResponse;
import com.inteliroadmap.backend.domain.dto.response.market.SkillTrendResponse;

import com.inteliroadmap.backend.domain.dto.response.market.SkillPostingsResponse;
import com.inteliroadmap.backend.services.MarketTrendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/market-trends")
@RequiredArgsConstructor
@Validated
@Tag(name = "Market Trends", description = "APIs for IT Market Trend Analytics and Dashboards")
public class MarketTrendController {

    private final MarketTrendService marketTrendService;

    @GetMapping("/companies/top-hiring")
    @Operation(summary = "Get top hiring companies", description = "Returns companies with the most recruitment posts.")
    public ResponseEntity<List<CompanyTrendResponse>> getTopHiringCompanies(
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "Limit must be at least 1") @Max(value = 100, message = "Limit must not exceed 100") int limit,
            @RequestParam(required = false) @Min(value = 1, message = "days must be at least 1") @Max(value = 365, message = "days must not exceed 365") Integer days,
            @RequestParam(required = false) UUID careerId,
            @RequestParam(required = false) String seniority) {
        // days omitted keeps the original all-time behaviour, so an existing client
        // that never learns about the parameter sees exactly what it saw before.
        if (careerId != null) return ResponseEntity.ok(marketTrendService.getTopHiringCompanies(
                limit, days == null ? MarketTrendService.DEFAULT_WINDOW_DAYS : days, careerId, seniority));
        return ResponseEntity.ok(days == null
                ? marketTrendService.getTopHiringCompanies(limit)
                : marketTrendService.getTopHiringCompanies(limit, days));
    }

    /**
     * The postings behind a skill's number.
     *
     * <p>Readable by any signed-in user, like the rest of this controller: the
     * postings are public ads, and the whole point is that the student can check
     * our arithmetic against the source.
     */
    @GetMapping("/skills/{skillId}/postings")
    @Operation(summary = "Get the postings behind a skill's demand figure",
            description = "The actual job ads that mention this skill, newest first, with the true total.")
    public ResponseEntity<SkillPostingsResponse> getPostingsForSkill(
            @PathVariable UUID skillId,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "limit must be at least 1")
            @Max(value = 50, message = "limit must not exceed 50") int limit) {
        return ResponseEntity.ok(marketTrendService.getPostingsForSkill(skillId, limit));
    }

    @GetMapping("/skills/trending")
    @Operation(summary = "Get skill trends over time", description = "Returns historical jobs needed data for top skills.")
    public ResponseEntity<List<SkillTrendResponse>> getSkillTrends(
            @RequestParam(required = false) @Min(value = 1, message = "days must be at least 1") @Max(value = 365, message = "days must not exceed 365") Integer days,
            @RequestParam(required = false) UUID careerId, @RequestParam(required = false) String seniority) {
        if (careerId != null) return ResponseEntity.ok(marketTrendService.getSkillTrends(
                days == null ? MarketTrendService.DEFAULT_WINDOW_DAYS : days, careerId, seniority));
        return ResponseEntity.ok(days == null
                ? marketTrendService.getSkillTrends()
                : marketTrendService.getSkillTrends(days));
    }

    @GetMapping("/salary-overview")
    @Operation(summary = "Get salary distribution", description = "Returns job counts grouped by salary ranges based on actual recruitment data.")
    public ResponseEntity<List<SalaryTrendResponse>> getSalaryDistribution(
            @RequestParam(required = false) @Min(value = 1, message = "days must be at least 1") @Max(value = 365, message = "days must not exceed 365") Integer days,
            @RequestParam(required = false) UUID careerId, @RequestParam(required = false) String seniority) {
        if (careerId != null) return ResponseEntity.ok(marketTrendService.getSalaryDistribution(
                days == null ? MarketTrendService.DEFAULT_WINDOW_DAYS : days, careerId, seniority));
        return ResponseEntity.ok(days == null
                ? marketTrendService.getSalaryDistribution()
                : marketTrendService.getSalaryDistribution(days));
    }

    @GetMapping("/freshness")
    @Operation(summary = "How current the market data is",
            description = "Distinct jobs in the window, how many had never been advertised before, "
                    + "and the most recent posting date on file. Lets the UI state the period "
                    + "instead of implying the figures are from today.")
    public ResponseEntity<FreshnessResponse> getFreshness(
            @RequestParam(defaultValue = "30") @Min(value = 1, message = "days must be at least 1") @Max(value = 365, message = "days must not exceed 365") int days,
            @RequestParam(required = false) UUID careerId, @RequestParam(required = false) String seniority) {
        if (careerId != null) return ResponseEntity.ok(marketTrendService.getFreshness(days, careerId, seniority));
        return ResponseEntity.ok(marketTrendService.getFreshness(days));
    }

    @PostMapping("/classify")
    @Operation(summary = "Label postings that have no seniority yet",
            description = "One-shot backfill for rows scraped before seniority classification existed. "
                    + "Idempotent - already-labelled rows are skipped.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Integer> classifyRecruitments() {
        return ResponseEntity.ok(marketTrendService.classifyUnlabelledRecruitments());
    }
}
