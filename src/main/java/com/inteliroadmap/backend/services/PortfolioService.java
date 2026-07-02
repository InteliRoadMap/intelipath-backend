package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.PortfolioUpsertRequest;
import com.inteliroadmap.backend.domain.dto.request.RequestReviewRequest;
import com.inteliroadmap.backend.domain.dto.response.PortfolioResponse;

import java.util.UUID;

public interface PortfolioService {

    PortfolioResponse getPortfolio();

    PortfolioResponse getPortfolioBySlug(String slug);

    PortfolioResponse getPortfolioByStudentId(UUID studentId);

    PortfolioResponse upsertPortfolio(PortfolioUpsertRequest request);

    void requestReview(RequestReviewRequest request);
}
