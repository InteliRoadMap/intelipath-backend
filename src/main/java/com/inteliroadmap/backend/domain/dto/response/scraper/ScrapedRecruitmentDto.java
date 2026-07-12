package com.inteliroadmap.backend.domain.dto.response.scraper;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Map;

/** Processed recruitment from the AI service (processed_recruitments). */
@Data
public class ScrapedRecruitmentDto {
    @JsonProperty("recruitment_id")
    private String recruitmentId;

    // { link, title, salary, location, experience }
    @JsonProperty("recruitment_infos")
    private Map<String, Object> recruitmentInfos;

    // { tags, descriptions, general_infos, related_tags }  (AI-summarised)
    private Map<String, Object> descriptions;

    @JsonProperty("application_deadline")
    private String applicationDeadline;
}
