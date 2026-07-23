package com.inteliroadmap.backend.domain.dto.response.scraper;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Map;

/** Processed company from the AI service (processed_companies). */
@Data
public class ScrapedCompanyResponse {
    @JsonProperty("company_id")
    private String companyId;

    // { link, logo, name }
    private Map<String, Object> signatures;

    // { info, contact }  (AI-summarised)
    private Map<String, Object> infos;
}
