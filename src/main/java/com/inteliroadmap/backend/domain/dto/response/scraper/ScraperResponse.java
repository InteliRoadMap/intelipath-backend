package com.inteliroadmap.backend.domain.dto.response.scraper;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class ScraperResponse {
    @JsonProperty("recruitment_posts")
    private List<ScrapedPostResponse> recruitmentPosts;

    @JsonProperty("processed_companies")
    private List<ScrapedCompanyResponse> companies;

    @JsonProperty("processed_recruitments")
    private List<ScrapedRecruitmentResponse> recruitments;
}
