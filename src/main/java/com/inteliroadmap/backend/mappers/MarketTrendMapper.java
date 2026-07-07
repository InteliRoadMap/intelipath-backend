package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.dto.response.market.MarketTrendResponse;
import com.inteliroadmap.backend.domain.entity.Company;
import com.inteliroadmap.backend.domain.entity.SkillTrend;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class MarketTrendMapper {

    public MarketTrendResponse.CompanyTrendResponse toCompanyTrendResponse(Company company, long recruitmentCount) {
        if (company == null) {
            return null;
        }
        var sig = company.getSignatures();
        return MarketTrendResponse.CompanyTrendResponse.builder()
                .topCvCompanyId(company.getTopCvCompanyId())
                .name(ScraperMapper.str(sig, "name"))
                .logo(ScraperMapper.str(sig, "logo"))
                .companyLink(ScraperMapper.str(sig, "link"))
                .recruitmentCount((int) recruitmentCount)
                .build();
    }

    public MarketTrendResponse.TrendDataPoint toTrendDataPoint(SkillTrend skillTrend) {
        if (skillTrend == null) {
            return null;
        }
        return MarketTrendResponse.TrendDataPoint.builder()
                .date(skillTrend.getWeekStamp())
                .jobsNeeded(skillTrend.getJobsNeeded() != null ? skillTrend.getJobsNeeded() : 0)
                .build();
    }

    public MarketTrendResponse.SkillTrendResponse toSkillTrendResponse(String skillName, List<SkillTrend> skillTrends) {
        if (skillTrends == null) {
            return MarketTrendResponse.SkillTrendResponse.builder()
                    .skillName(skillName)
                    .dataPoints(List.of())
                    .build();
        }

        List<MarketTrendResponse.TrendDataPoint> dataPoints = skillTrends.stream()
                .sorted(Comparator.comparing(SkillTrend::getWeekStamp))
                .map(this::toTrendDataPoint)
                .collect(Collectors.toList());

        return MarketTrendResponse.SkillTrendResponse.builder()
                .skillName(skillName)
                .dataPoints(dataPoints)
                .build();
    }
}
