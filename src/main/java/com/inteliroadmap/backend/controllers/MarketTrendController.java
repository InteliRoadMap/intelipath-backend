package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.response.market.MarketTrendResponse;
import com.inteliroadmap.backend.services.MarketTrendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/market-trends")
@RequiredArgsConstructor
@Tag(name = "Market Trends", description = "APIs for IT Market Trend Analytics and Dashboards")
public class MarketTrendController {

    private final MarketTrendService marketTrendService;

    @GetMapping("/companies/top-hiring")
    @Operation(summary = "Get top hiring companies", description = "Returns companies with the most recruitment posts.")
    public ResponseEntity<List<MarketTrendResponse.CompanyTrendResponse>> getTopHiringCompanies(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(marketTrendService.getTopHiringCompanies(limit));
    }

    @GetMapping("/skills/trending")
    @Operation(summary = "Get skill trends over time", description = "Returns historical jobs needed data for top skills.")
    public ResponseEntity<List<MarketTrendResponse.SkillTrendResponse>> getSkillTrends() {
        return ResponseEntity.ok(marketTrendService.getSkillTrends());
    }

    @GetMapping("/salary-overview")
    @Operation(summary = "Get salary distribution", description = "Returns job counts grouped by salary ranges based on actual recruitment data.")
    public ResponseEntity<List<MarketTrendResponse.SalaryTrendResponse>> getSalaryDistribution() {
        return ResponseEntity.ok(marketTrendService.getSalaryDistribution());
    }
}
