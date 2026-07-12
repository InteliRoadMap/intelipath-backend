package com.inteliroadmap.backend.domain.dto.response.scraper;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ScrapedPostResponse {
    @JsonProperty("post_id")
    private String postId;

    @JsonProperty("company_id")
    private String companyId;

    @JsonProperty("recruitment_id")
    private String recruitmentId;

    @JsonProperty("expire_at")
    private String expireAt;
}
