package com.inteliroadmap.backend.domain.dto.response.scraper;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ScrapedRecruitmentDto {
    @JsonProperty("recruitment_id")
    private String recruitmentId;

    @JsonProperty("recruitment_link")
    private String recruitmentLink;

    private String title;
    private String salary;
    private String location;
    private String experience;

    @JsonProperty("application_deadline")
    private String applicationDeadline;

    private Map<String, Object> tags;

    private Map<String, Object> descriptions;

    @JsonProperty("general_infos")
    private Map<String, Object> generalInfos;

    @JsonProperty("related_tags")
    private Map<String, Object> relatedTags;
}
