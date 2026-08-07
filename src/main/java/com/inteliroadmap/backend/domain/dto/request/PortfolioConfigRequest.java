package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PortfolioConfigRequest {
    @Size(max = 50, message = "Theme must not exceed 50 characters")
    private String theme;
    private Map<String, Object> themeColors;
    private Map<String, Object> fonts;
    private Map<String, Object> heroSection;
    private Map<String, Object> skillsSection;
}
