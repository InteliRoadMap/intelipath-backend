package com.inteliroadmap.backend.domain.dto.response.portfolio;

import com.inteliroadmap.backend.domain.enums.EvidenceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PortfolioConfigResponse {
    private String theme;
    private Map<String, Object> themeColors;
    private Map<String, Object> fonts;
    private Map<String, Object> heroSection;
    private Map<String, Object> skillsSection;
}
