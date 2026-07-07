package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.GithubImportRequest;
import com.inteliroadmap.backend.domain.dto.response.portfolio.PortfolioResponse;
import com.inteliroadmap.backend.clients.GithubApiClient;
import com.inteliroadmap.backend.ai.analyzer.PortfolioAiAnalyzer;
import com.inteliroadmap.backend.services.GithubPortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface GithubPortfolioService {

    public PortfolioResponse.PortfolioProjectResponse importFromGithub(GithubImportRequest request) ;
}
