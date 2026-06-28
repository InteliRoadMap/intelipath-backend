package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.mappers.MarketTrendMapper;
import com.inteliroadmap.backend.domain.dto.response.market.MarketTrendResponse;
import com.inteliroadmap.backend.domain.entity.Company;
import com.inteliroadmap.backend.domain.entity.SkillTrend;
import com.inteliroadmap.backend.repositories.CompanyRepository;
import com.inteliroadmap.backend.repositories.RecruitmentRepository;
import com.inteliroadmap.backend.repositories.SkillTrendRepository;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.services.MarketTrendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public interface MarketTrendService {

    public List<MarketTrendResponse.CompanyTrendResponse> getTopHiringCompanies(int limit) ;

    public List<MarketTrendResponse.SkillTrendResponse> getSkillTrends() ;

    public List<MarketTrendResponse.SalaryTrendResponse> getSalaryDistribution() ;
}
