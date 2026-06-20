package com.inteliroadmap.backend.domain.dto.response.scraper;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class ScraperResponseDto {
    @JsonProperty("recruitment_posts")
    private List<ScrapedPostDto> recruitmentPosts;

    private List<ScrapedCompanyDto> companies;

    private List<ScrapedRecruitmentDto> recruitments;
}
