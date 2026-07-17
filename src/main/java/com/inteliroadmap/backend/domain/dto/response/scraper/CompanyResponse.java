package com.inteliroadmap.backend.domain.dto.response.scraper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CompanyResponse {
    private String companyId;
    private String companyLink;
    private String logo;
    private String name;
    private List<String> introductions;
    private Map<String, Object> infos;
    private List<String> contacts;
}
