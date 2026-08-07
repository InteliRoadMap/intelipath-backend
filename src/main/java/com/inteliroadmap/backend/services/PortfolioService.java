package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.PortfolioUpsertRequest;
import com.inteliroadmap.backend.domain.dto.request.RequestReviewRequest;
import com.inteliroadmap.backend.domain.dto.response.portfolio.PortfolioResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.PortfolioAboutDraftResponse;

public interface PortfolioService {

    PortfolioResponse getPortfolio();

    PortfolioResponse getPortfolioBySlug(String slug);

    PortfolioResponse upsertPortfolio(PortfolioUpsertRequest request);

    void requestReview(RequestReviewRequest request);

    PortfolioAboutDraftResponse generateAboutDraft();
}
