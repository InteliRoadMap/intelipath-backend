package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.GithubImportRequest;
import com.inteliroadmap.backend.domain.dto.response.PortfolioResponse;

public interface GithubPortfolioService {

    PortfolioResponse.PortfolioProjectResponse importFromGithub(GithubImportRequest request) ;
}
