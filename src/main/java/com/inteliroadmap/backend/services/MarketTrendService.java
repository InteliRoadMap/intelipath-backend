package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.mappers.MarketTrendMapper;
import com.inteliroadmap.backend.domain.dto.response.market.MarketTrendResponse;
import com.inteliroadmap.backend.domain.entity.Company;
import com.inteliroadmap.backend.domain.entity.SkillTrend;
import com.inteliroadmap.backend.repositories.CompanyRepository;
import com.inteliroadmap.backend.repositories.RecruitmentRepository;
import com.inteliroadmap.backend.repositories.SkillTrendRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketTrendService {

    private final CompanyRepository companyRepository;
    private final SkillTrendRepository skillTrendRepository;
    private final RecruitmentRepository recruitmentRepository;
    private final MarketTrendMapper marketTrendMapper;

    @Transactional(readOnly = true)
    public List<MarketTrendResponse.CompanyTrendResponse> getTopHiringCompanies(int limit) {
        List<Company> topCompanies = companyRepository.findTopHiringCompanies(PageRequest.of(0, limit));
        
        return topCompanies.stream()
                .map(marketTrendMapper::toCompanyTrendResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MarketTrendResponse.SkillTrendResponse> getSkillTrends() {
        List<SkillTrend> allTrends = skillTrendRepository.findAll();

        Map<String, List<SkillTrend>> trendsBySkill = allTrends.stream()
                .filter(t -> t.getSkill() != null && t.getSkill().getSkillName() != null)
                .collect(Collectors.groupingBy(t -> t.getSkill().getSkillName()));

        return trendsBySkill.entrySet().stream()
                .map(entry -> marketTrendMapper.toSkillTrendResponse(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> Integer.compare(
                        b.getDataPoints().stream().mapToInt(MarketTrendResponse.TrendDataPoint::getJobsNeeded).sum(),
                        a.getDataPoints().stream().mapToInt(MarketTrendResponse.TrendDataPoint::getJobsNeeded).sum()
                ))
                .limit(10) // Only top 10 skills
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MarketTrendResponse.SalaryTrendResponse> getSalaryDistribution() {
        List<String> rawSalaries = recruitmentRepository.findAllSalaries();

        int count0To10 = 0;
        int count10To20 = 0;
        int count20To30 = 0;
        int count30To50 = 0;
        int countOver50 = 0;

        Pattern numberPattern = Pattern.compile("(\\d+)");

        for (String salaryText : rawSalaries) {
            salaryText = salaryText.toLowerCase();
            if (salaryText.contains("thỏa thuận") || salaryText.contains("thương lượng")) {
                continue; // Ignore negotiable salaries for distribution
            }

            Matcher matcher = numberPattern.matcher(salaryText);
            List<Long> numbers = new ArrayList<>();
            while (matcher.find()) {
                numbers.add(Long.parseLong(matcher.group()));
            }

            if (numbers.isEmpty()) continue;

            // Calculate average if range is provided (e.g. 10 - 20)
            double avg = numbers.stream().mapToLong(Long::longValue).average().orElse(0);
            
            // Adjust for unit (some say "1000 USD", some say "20 triệu")
            // Assuming TopCV data is mostly in million VND or USD. If > 100, might be USD, multiply by 25.
            if (avg > 100 && avg <= 10000) {
                avg = (avg * 25) / 1000.0; // Convert USD to million VND approx
            }

            if (avg > 0 && avg <= 10) count0To10++;
            else if (avg > 10 && avg <= 20) count10To20++;
            else if (avg > 20 && avg <= 30) count20To30++;
            else if (avg > 30 && avg <= 50) count30To50++;
            else if (avg > 50) countOver50++;
        }

        return List.of(
                MarketTrendResponse.SalaryTrendResponse.builder().category("Dưới 10 triệu").jobCount(count0To10).build(),
                MarketTrendResponse.SalaryTrendResponse.builder().category("10 - 20 triệu").jobCount(count10To20).build(),
                MarketTrendResponse.SalaryTrendResponse.builder().category("20 - 30 triệu").jobCount(count20To30).build(),
                MarketTrendResponse.SalaryTrendResponse.builder().category("30 - 50 triệu").jobCount(count30To50).build(),
                MarketTrendResponse.SalaryTrendResponse.builder().category("Trên 50 triệu").jobCount(countOver50).build()
        );
    }
}
