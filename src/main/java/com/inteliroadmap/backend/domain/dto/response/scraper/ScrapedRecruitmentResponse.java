package com.inteliroadmap.backend.domain.dto.response.scraper;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Map;

/** Processed recruitment from the AI service (processed_recruitments). */
@Data
public class ScrapedRecruitmentResponse {
    @JsonProperty("recruitment_id")
    private String recruitmentId;

    // { link, title, salary, location, experience }
    @JsonProperty("recruitment_infos")
    private Map<String, Object> recruitmentInfos;

    // { tags, descriptions, general_infos, related_tags }  (AI-summarised)
    private Map<String, Object> descriptions;

    @JsonProperty("posted_date")
    private String postedDate;

    /**
     * Identity of the job rather than of this posting: company + title + location.
     * A re-posted listing arrives with a new recruitment id but the same key, which
     * is what lets a count collapse the two instead of reporting demand twice.
     */
    @JsonProperty("dedup_key")
    private String dedupKey;

    @JsonProperty("application_deadline")
    private String applicationDeadline;
}
