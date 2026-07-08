package com.inteliroadmap.backend.domain.dto.response.scraper;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ScrapedCompanyDto {
    @JsonProperty("company_id")
    private String companyId;

//    @JsonProperty("company_link")
//    private String companyLink;
//
//    private String logo;
//    private String name;
//
//    private List<String> introduction;
//
//    private Map<String, Object> info;
//
//    private List<String> contact;

    private Map<String, String> signatures;
    private Map<String, String> infos;
}
