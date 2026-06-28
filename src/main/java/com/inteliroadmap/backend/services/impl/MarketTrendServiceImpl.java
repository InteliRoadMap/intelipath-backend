package com.inteliroadmap.backend.services.impl;

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

/**
 * Implementation of {@link MarketTrendService} for analyzing and retrieving
 * market trend data. This service provides insights such as top hiring companies,
 * skill demand trends, and salary distributions across the job market.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketTrendServiceImpl implements MarketTrendService {

    private final CompanyRepository companyRepository;
    private final SkillTrendRepository skillTrendRepository;
    private final SkillRepository skillRepository;
    private final RecruitmentRepository recruitmentRepository;
    private final MarketTrendMapper marketTrendMapper;

    /**
     * Retrieves a list of the top hiring companies limited by the specified amount.
     * 
     * @param limit The maximum number of top hiring companies to return
     * @return A list of {@link MarketTrendResponse.CompanyTrendResponse} representing the top companies
     */
    @Transactional(readOnly = true)
    @Override
    public List<MarketTrendResponse.CompanyTrendResponse> getTopHiringCompanies(int limit) {
        // Query the database for the top companies based on hiring volume, limiting the results by 'limit'
        List<Company> topCompanies = companyRepository.findTopHiringCompanies(PageRequest.of(0, limit));
        
        // Map the entity models to DTO responses using MarketTrendMapper and return as a List
        return topCompanies.stream()
                .map(marketTrendMapper::toCompanyTrendResponse)
                .collect(Collectors.toList());
    }

    /**
     * Aggregates and retrieves the top 10 skill trends based on job demand.
     * 
     * @return A list of {@link MarketTrendResponse.SkillTrendResponse} detailing skill demands
     */
    @Transactional(readOnly = true)
    @Override
    public List<MarketTrendResponse.SkillTrendResponse> getSkillTrends() {
        // Fetch all skill trends from the database
        List<SkillTrend> allTrends = skillTrendRepository.findAll();

        // Group the trends by the resolved skill name. Filter out trends with null skill IDs.
        Map<String, List<SkillTrend>> trendsBySkill = allTrends.stream()
                .filter(t -> t.getSkillId() != null)
                .collect(Collectors.groupingBy(t -> {
                    // Look up the actual skill entity to retrieve its name, default to "Unknown" if not found
                    Skill skill = skillRepository.findById(t.getSkillId()).orElse(null);
                    return skill != null ? skill.getSkillName() : "Unknown";
                }));

        // Convert the grouped map entries into a list of SkillTrendResponse objects
        return trendsBySkill.entrySet().stream()
                .map(entry -> marketTrendMapper.toSkillTrendResponse(entry.getKey(), entry.getValue()))
                // Sort the responses by total jobs needed in descending order (highest demand first)
                .sorted((a, b) -> Integer.compare(
                        b.getDataPoints().stream().mapToInt(MarketTrendResponse.TrendDataPoint::getJobsNeeded).sum(),
                        a.getDataPoints().stream().mapToInt(MarketTrendResponse.TrendDataPoint::getJobsNeeded).sum()
                ))
                .limit(10) // Only top 10 skills
                .collect(Collectors.toList());
    }

    /**
     * Calculates and returns the salary distribution across predefined salary ranges
     * based on available recruitment data.
     * 
     * @return A list of {@link MarketTrendResponse.SalaryTrendResponse} representing job counts per salary bracket
     */
    @Transactional(readOnly = true)
    @Override
    public List<MarketTrendResponse.SalaryTrendResponse> getSalaryDistribution() {
        // Fetch all raw salary strings from the recruitment records
        List<String> rawSalaries = recruitmentRepository.findAllSalaries();

        // Initialize counters for the predefined salary brackets
        int count0To10 = 0;
        int count10To20 = 0;
        int count20To30 = 0;
        int count30To50 = 0;
        int countOver50 = 0;

        // Regex pattern to extract digits (potential salary values) from text
        Pattern numberPattern = Pattern.compile("(\\d+)");

        for (String salaryText : rawSalaries) {
            salaryText = salaryText.toLowerCase();
            // Skip non-numerical negotiable salary entries
            if (salaryText.contains("thỏa thuận") || salaryText.contains("thương lượng")) {
                continue; // Ignore negotiable salaries for distribution
            }

            Matcher matcher = numberPattern.matcher(salaryText);
            List<Integer> numbers = new ArrayList<>();
            // Find and collect all numerical values found in the salary string
            while (matcher.find()) {
                numbers.add(Integer.parseInt(matcher.group()));
            }

            // Skip if no numbers were parsed
            if (numbers.isEmpty()) continue;

            // Calculate average if range is provided (e.g. 10 - 20)
            double avg = numbers.stream().mapToInt(Integer::intValue).average().orElse(0);
            
            // Adjust for unit (some say "1000 USD", some say "20 triệu")
            // Assuming TopCV data is mostly in million VND or USD. If > 100, might be USD, multiply by 25.
            if (avg > 100 && avg <= 10000) {
                avg = (avg * 25) / 1000.0; // Convert USD to million VND approx
            }

            // Increment the respective salary bracket counter based on the computed average
            if (avg > 0 && avg <= 10) count0To10++;
            else if (avg > 10 && avg <= 20) count10To20++;
            else if (avg > 20 && avg <= 30) count20To30++;
            else if (avg > 30 && avg <= 50) count30To50++;
            else if (avg > 50) countOver50++;
        }

        // Build and return the final distribution list representing each salary category
        return List.of(
                MarketTrendResponse.SalaryTrendResponse.builder().category("Dưới 10 triệu").jobCount(count0To10).build(),
                MarketTrendResponse.SalaryTrendResponse.builder().category("10 - 20 triệu").jobCount(count10To20).build(),
                MarketTrendResponse.SalaryTrendResponse.builder().category("20 - 30 triệu").jobCount(count20To30).build(),
                MarketTrendResponse.SalaryTrendResponse.builder().category("30 - 50 triệu").jobCount(count30To50).build(),
                MarketTrendResponse.SalaryTrendResponse.builder().category("Trên 50 triệu").jobCount(countOver50).build()
        );
    }
}
