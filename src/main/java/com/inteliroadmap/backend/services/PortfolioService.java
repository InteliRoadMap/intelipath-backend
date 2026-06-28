package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.PortfolioUpsertRequest;
import com.inteliroadmap.backend.domain.dto.response.PortfolioResponse;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.*;
import com.inteliroadmap.backend.mappers.PortfolioMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

public interface PortfolioService {

    public PortfolioResponse getPortfolio() ;

    public PortfolioResponse getPortfolioBySlug(String slug) ;

    public PortfolioResponse upsertPortfolio(PortfolioUpsertRequest request) ;
}
