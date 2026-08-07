package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.response.scraper.CompanyResponse;
import com.inteliroadmap.backend.domain.dto.response.scraper.RecruitmentPostResponse;
import com.inteliroadmap.backend.domain.dto.response.scraper.RecruitmentResponse;
import com.inteliroadmap.backend.exceptions.GlobalExceptionHandler.ErrorResponse;
import java.util.List;
import com.inteliroadmap.backend.services.ScraperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recruitment-posts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Scraper service", description = "Scraper service endpoints")
@PreAuthorize("isAuthenticated()")
public class RecruitmentPostsController {

    private final ScraperService scraperService;

    @GetMapping("/")
    @Operation(description = "Get all recruitment posts")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Recruitment posts",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RecruitmentPostResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No Posts Found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal Server Error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<List<RecruitmentPostResponse>> getRecruitmentPosts(
            @RequestParam(required = false) String seniority,
            @RequestParam(required = false) java.util.UUID careerId) {
        log.info("RecruitmentPostsController: Getting all recruitment posts");
        return ResponseEntity.ok(scraperService.getRecruitmentPosts(seniority, careerId));
    }

    @GetMapping("/company/{companyId}")
    @Operation(description = "Search Company by ID")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Search Company by ID",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CompanyResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Company Not Found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal Server Error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<CompanyResponse> getCompanyInfos(@PathVariable String companyId) {
        log.info("RecruitmentPostsController: Getting Company info for ID {}", companyId);
        return ResponseEntity.ok(scraperService.getCompanyInfos(companyId));
    }

    @GetMapping("/recruitment/{recruitmentId}")
    @Operation(description = "Search Recruitment by ID")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Search Recruitment by ID",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RecruitmentResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Recruitment Not Found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal Server Error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<RecruitmentResponse> getRecruitmentInfos(@PathVariable String recruitmentId) {
        log.info("RecruitmentPostsController: Getting Recruitment info for ID {}", recruitmentId);
        return ResponseEntity.ok(scraperService.getRecruitmentInfos(recruitmentId));
    }
}
