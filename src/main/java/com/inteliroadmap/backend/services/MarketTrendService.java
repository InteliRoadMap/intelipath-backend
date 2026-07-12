package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.market.MarketTrendResponse;

import java.util.List;

public interface MarketTrendService {

    List<MarketTrendResponse.CompanyTrendResponse> getTopHiringCompanies(int limit) ;

    List<MarketTrendResponse.SkillTrendResponse> getSkillTrends() ;

    List<MarketTrendResponse.SalaryTrendResponse> getSalaryDistribution() ;
}
