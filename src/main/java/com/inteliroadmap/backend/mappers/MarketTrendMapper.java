package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.dto.response.market.CompanyTrendResponse;
import com.inteliroadmap.backend.domain.dto.response.market.SkillTrendResponse;
import com.inteliroadmap.backend.domain.dto.response.market.TrendDataPoint;

import com.inteliroadmap.backend.domain.entity.Company;
import com.inteliroadmap.backend.domain.entity.SkillTrend;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class MarketTrendMapper {

    public CompanyTrendResponse toCompanyTrendResponse(Company company, long recruitmentCount) {
        if (company == null) {
            return null;
        }
        var sig = company.getSignatures();
        return CompanyTrendResponse.builder()
                .topCvCompanyId(company.getTopCvCompanyId())
                .name(ScraperMapper.str(sig, "name"))
                .logo(ScraperMapper.str(sig, "logo"))
                .companyLink(ScraperMapper.str(sig, "link"))
                .recruitmentCount((int) recruitmentCount)
                .build();
    }

    public TrendDataPoint toTrendDataPoint(SkillTrend skillTrend) {
        if (skillTrend == null) {
            return null;
        }
        return TrendDataPoint.builder()
                .date(skillTrend.getWeekStamp())
                .jobsNeeded(skillTrend.getJobsNeeded() != null ? skillTrend.getJobsNeeded() : 0)
                .build();
    }

    public SkillTrendResponse toSkillTrendResponse(String skillName, List<SkillTrend> skillTrends) {
        if (skillTrends == null) {
            return SkillTrendResponse.builder()
                    .skillName(skillName)
                    .dataPoints(List.of())
                    .build();
        }

        List<TrendDataPoint> dataPoints = skillTrends.stream()
                .sorted(Comparator.comparing(SkillTrend::getWeekStamp))
                .map(this::toTrendDataPoint)
                .collect(Collectors.toList());

        return SkillTrendResponse.builder()
                .skillName(skillName)
                .dataPoints(dataPoints)
                .build();
    }
}
