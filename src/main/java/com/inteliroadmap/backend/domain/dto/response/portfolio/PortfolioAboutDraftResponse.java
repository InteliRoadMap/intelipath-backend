package com.inteliroadmap.backend.domain.dto.response.portfolio;

/** AI-written portfolio copy returned as a draft; generating it never persists data. */
public record PortfolioAboutDraftResponse(String role, String description, String objective) {
}
