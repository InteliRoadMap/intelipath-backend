package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.response.market.CompanyTrendResponse;
import com.inteliroadmap.backend.domain.dto.response.market.FreshnessResponse;
import com.inteliroadmap.backend.domain.dto.response.market.PostingResponse;
import com.inteliroadmap.backend.domain.dto.response.market.SalaryTrendResponse;
import com.inteliroadmap.backend.domain.dto.response.market.SkillTrendResponse;
import com.inteliroadmap.backend.domain.dto.response.market.TrendDataPoint;

import com.inteliroadmap.backend.mappers.MarketTrendMapper;
import com.inteliroadmap.backend.domain.entity.Company;
import com.inteliroadmap.backend.components.SeniorityClassifier;
import com.inteliroadmap.backend.domain.entity.Recruitment;
import com.inteliroadmap.backend.domain.entity.SkillTrend;
import com.inteliroadmap.backend.repositories.CompanyRepository;
import com.inteliroadmap.backend.repositories.RecruitmentRepository;
import com.inteliroadmap.backend.repositories.SkillTrendRepository;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.domain.dto.response.market.SkillPostingsResponse;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.services.MarketTrendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
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
    private final SeniorityClassifier seniorityClassifier;

    /**
     * Retrieves a list of the top hiring companies limited by the specified amount.
     * 
     * @param limit The maximum number of top hiring companies to return
     * @return A list of {@link CompanyTrendResponse} representing the top companies
     */
    @Transactional(readOnly = true)
    @Override
    public List<CompanyTrendResponse> getTopHiringCompanies(int limit) {
        // Query the database for the top companies WITH their post count.
        List<Object[]> rows = companyRepository.findTopHiringCompaniesWithCount(PageRequest.of(0, limit));

        // Map [Company, count] rows to DTO responses using MarketTrendMapper.
        return rows.stream()
                .map(row -> marketTrendMapper.toCompanyTrendResponse((Company) row[0], ((Number) row[1]).longValue()))
                .collect(Collectors.toList());
    }

    /**
     * Aggregates and retrieves the top 10 skill trends based on job demand.
     * 
     * @return A list of {@link SkillTrendResponse} detailing skill demands
     */
    @Transactional(readOnly = true)
    @Override
    public List<SkillTrendResponse> getSkillTrends() {
        // Fetch all skill trends from the database
        List<SkillTrend> allTrends = skillTrendRepository.findAll();

        // Group the trends by the resolved skill name. Filter out trends with null skill IDs.
        Map<String, List<SkillTrend>> trendsBySkill = allTrends.stream()
                .filter(t -> t.getSkill().getSkillId() != null)
                .collect(Collectors.groupingBy(t -> {
                    // Look up the actual skill entity to retrieve its name, default to "Unknown" if not found
                    Skill skill = skillRepository.findById(t.getSkill().getSkillId()).orElse(null);
                    return skill != null ? skill.getSkillName() : "Unknown";
                }));

        // Convert the grouped map entries into a list of SkillTrendResponse objects
        return trendsBySkill.entrySet().stream()
                .map(entry -> marketTrendMapper.toSkillTrendResponse(entry.getKey(), entry.getValue()))
                // Sort the responses by total jobs needed in descending order (highest demand first)
                .sorted((a, b) -> Integer.compare(
                        b.getDataPoints().stream().mapToInt(TrendDataPoint::getJobsNeeded).sum(),
                        a.getDataPoints().stream().mapToInt(TrendDataPoint::getJobsNeeded).sum()
                ))
                .limit(10) // Only top 10 skills
                .collect(Collectors.toList());
    }

    /**
     * Calculates and returns the salary distribution across predefined salary ranges
     * based on available recruitment data.
     * 
     * @return A list of {@link SalaryTrendResponse} representing job counts per salary bracket
     */
    @Transactional(readOnly = true)
    @Override
    public List<SalaryTrendResponse> getSalaryDistribution() {
        // Fetch all raw salary strings from the recruitment records
        return bucketSalaries(recruitmentRepository.findAllSalaries());
    }

    @Transactional(readOnly = true)
    @Override
    public List<SalaryTrendResponse> getSalaryDistribution(int windowDays) {
        // Same buckets, but only over postings recent enough to be worth planning
        // around, and counting a re-advertised job once.
        return bucketSalaries(recruitmentRepository.findSalariesSince(since(windowDays)));
    }

    private List<SalaryTrendResponse> bucketSalaries(List<String> rawSalaries) {
        // Initialize counters for the predefined salary brackets
        int count0To10 = 0;
        int count10To20 = 0;
        int count20To30 = 0;
        int count30To50 = 0;
        int countOver50 = 0;

        for (String salaryText : rawSalaries) {
            OptionalDouble parsed = monthlySalaryInMillions(salaryText);
            if (parsed.isEmpty()) {
                continue; // Negotiable, hidden or otherwise unquantified
            }
            double avg = parsed.getAsDouble();

            // Increment the respective salary bracket counter based on the computed average
            if (avg > 0 && avg <= 10) count0To10++;
            else if (avg > 10 && avg <= 20) count10To20++;
            else if (avg > 20 && avg <= 30) count20To30++;
            else if (avg > 30 && avg <= 50) count30To50++;
            else if (avg > 50) countOver50++;
        }

        // Build and return the final distribution list representing each salary category
        return List.of(
                SalaryTrendResponse.builder().category("Dưới 10 triệu").jobCount(count0To10).build(),
                SalaryTrendResponse.builder().category("10 - 20 triệu").jobCount(count10To20).build(),
                SalaryTrendResponse.builder().category("20 - 30 triệu").jobCount(count20To30).build(),
                SalaryTrendResponse.builder().category("30 - 50 triệu").jobCount(count30To50).build(),
                SalaryTrendResponse.builder().category("Trên 50 triệu").jobCount(countOver50).build()
        );
    }

    @Transactional
    @Override
    public int classifyUnlabelledRecruitments() {
        List<Recruitment> unlabelled = recruitmentRepository.findBySeniorityIsNull();
        if (unlabelled.isEmpty()) {
            return 0;
        }
        for (Recruitment r : unlabelled) {
            Map<String, Object> infos = r.getRecruitmentInfos();
            r.setSeniority(seniorityClassifier.classify(
                    infos == null ? null : String.valueOf(infos.get("title")),
                    infos == null ? null : String.valueOf(infos.get("experience"))).name());
            r.setClassifiedAt(LocalDateTime.now());
        }
        recruitmentRepository.saveAll(unlabelled);
        log.info("MarketTrendServiceImpl: classified {} previously unlabelled posting(s).", unlabelled.size());
        return unlabelled.size();
    }

    /** Start of the window, from a day count. */
    private LocalDate since(int windowDays) {
        int days = windowDays > 0 ? windowDays : MarketTrendService.DEFAULT_WINDOW_DAYS;
        return LocalDate.now().minusDays(days);
    }

    @Transactional(readOnly = true)
    @Override
    public List<CompanyTrendResponse> getTopHiringCompanies(int limit, int windowDays) {
        List<Object[]> rows = recruitmentRepository.findTopHiringCompanyIdsSince(since(windowDays), limit);

        List<CompanyTrendResponse> out = new ArrayList<>();
        for (Object[] row : rows) {
            String companyId = (String) row[0];
            long jobCount = ((Number) row[1]).longValue();
            // A posting can outlive its company row; skip rather than emit a blank card.
            companyRepository.findById(companyId).ifPresent(company ->
                    out.add(marketTrendMapper.toCompanyTrendResponse(company, jobCount)));
        }
        return out;
    }

    @Transactional(readOnly = true)
    @Override
    public List<SkillTrendResponse> getSkillTrends(int windowDays) {
        LocalDate from = since(windowDays);

        // "Trending" has to mean recent, not cumulative. The all-time form ranks a
        // skill that was in demand months ago above one hiring right now, which is
        // precisely backwards for someone deciding what to learn next.
        List<SkillTrend> recent = skillTrendRepository.findAll().stream()
                .filter(t -> t.getSkill() != null && t.getSkill().getSkillId() != null)
                .filter(t -> t.getWeekStamp() != null && !t.getWeekStamp().isBefore(from))
                .toList();

        Map<String, List<SkillTrend>> trendsBySkill = recent.stream()
                .collect(Collectors.groupingBy(t -> {
                    Skill skill = skillRepository.findById(t.getSkill().getSkillId()).orElse(null);
                    return skill != null ? skill.getSkillName() : "Unknown";
                }));

        return trendsBySkill.entrySet().stream()
                .map(entry -> marketTrendMapper.toSkillTrendResponse(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> Integer.compare(
                        b.getDataPoints().stream().mapToInt(TrendDataPoint::getJobsNeeded).sum(),
                        a.getDataPoints().stream().mapToInt(TrendDataPoint::getJobsNeeded).sum()
                ))
                .limit(10)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public FreshnessResponse getFreshness(int windowDays) {
        int days = windowDays > 0 ? windowDays : MarketTrendService.DEFAULT_WINDOW_DAYS;
        LocalDate from = since(days);
        return FreshnessResponse.builder()
                .windowDays(days)
                .jobsInWindow((int) recruitmentRepository.countDistinctJobsSince(from))
                .newJobs((int) recruitmentRepository.countGenuinelyNewSince(from))
                .latestPostedDate(recruitmentRepository.findLatestPostedDate())
                .build();
    }

    private String level(String seniority) {
        return seniority == null ? "" : seniority.trim().toUpperCase(java.util.Locale.ROOT);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyTrendResponse> getTopHiringCompanies(int limit, int windowDays, UUID careerId, String seniority) {
        if (careerId == null) return getTopHiringCompanies(limit, windowDays);
        List<CompanyTrendResponse> out = new ArrayList<>();
        for (Object[] row : recruitmentRepository.findTopHiringCompanyIdsScoped(
                since(windowDays), careerId, level(seniority), limit)) {
            companyRepository.findById((String) row[0]).ifPresent(company -> out.add(
                    marketTrendMapper.toCompanyTrendResponse(company, ((Number) row[1]).longValue())));
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalaryTrendResponse> getSalaryDistribution(int windowDays, UUID careerId, String seniority) {
        if (careerId == null) return getSalaryDistribution(windowDays);
        return bucketSalaries(recruitmentRepository.findSalariesScoped(
                since(windowDays), careerId, level(seniority)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SkillTrendResponse> getSkillTrends(int windowDays, UUID careerId, String seniority) {
        if (careerId == null) return getSkillTrends(windowDays);
        Map<String, List<TrendDataPoint>> points = new java.util.LinkedHashMap<>();
        for (Object[] row : recruitmentRepository.findSkillTrendsScoped(
                since(windowDays), careerId, level(seniority))) {
            LocalDate week = row[1] instanceof java.sql.Date d ? d.toLocalDate() : LocalDate.parse(String.valueOf(row[1]));
            points.computeIfAbsent(String.valueOf(row[0]), ignored -> new ArrayList<>())
                    .add(TrendDataPoint.builder().date(week).jobsNeeded(((Number) row[2]).intValue()).build());
        }
        return points.entrySet().stream()
                .map(e -> SkillTrendResponse.builder().skillName(e.getKey()).dataPoints(e.getValue()).build())
                .sorted((a, b) -> Integer.compare(
                        b.getDataPoints().stream().mapToInt(TrendDataPoint::getJobsNeeded).sum(),
                        a.getDataPoints().stream().mapToInt(TrendDataPoint::getJobsNeeded).sum()))
                .limit(10).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FreshnessResponse getFreshness(int windowDays, UUID careerId, String seniority) {
        if (careerId == null) return getFreshness(windowDays);
        int days = windowDays > 0 ? windowDays : DEFAULT_WINDOW_DAYS;
        String scopedLevel = level(seniority);
        return FreshnessResponse.builder().windowDays(days)
                .jobsInWindow((int) recruitmentRepository.countDistinctJobsScoped(since(days), careerId, scopedLevel))
                .newJobs((int) recruitmentRepository.countGenuinelyNewScoped(since(days), careerId, scopedLevel))
                .latestPostedDate(recruitmentRepository.findLatestPostedDateScoped(careerId, scopedLevel)).build();
    }

    /** Rough VND per USD, only ever used to bucket a posting, never to quote a figure. */
    private static final double USD_TO_VND = 25_000d;

    /** A thousands separator: the '.' or ',' sitting between a digit and a full group of three. */
    private static final Pattern GROUP_SEPARATOR = Pattern.compile("(?<=\\d)[.,](?=\\d{3}(?!\\d))");

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    /**
     * A dong marker written against the digits rather than spelled out: "50.000.000đ",
     * "30M", "20tr".
     *
     * <p>Only the ASCII suffixes take a word boundary, to stop "m" matching the start of
     * "month" in "1500 USD/month". "đ" cannot take one: it is not an ASCII word character,
     * so there is no boundary between it and the following space and the match would never
     * fire — which is exactly how "50.000.000đ" kept being read as dollars.
     */
    private static final Pattern DIGIT_VND_UNIT =
            Pattern.compile("\\d\\s*(?:đ|(?:tr|m)\\b)",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Reads a scraped salary string as an average monthly figure in millions of VND, or empty
     * when the posting does not actually quote one.
     *
     * <p>Two things used to go wrong. Digits were matched with {@code \d+}, so a thousands
     * separator split the number — "2,000 - 2,500 USD" parsed as 2, 0, 2 and 500, averaging
     * 126 rather than 2250. And the currency was guessed from magnitude ("bigger than 100 is
     * probably USD"), which then converted that 126 into ~3M VND and filed a $2,000-2,500 role,
     * worth around 56M, into the *lowest* bracket. Separators are stripped first and the
     * currency is read from the text instead, with VND wording winning so that a string
     * carrying both — the scraper can emit "22 million vnd USD" — is not treated as dollars.
     *
     * <p>Package-private and static so the parsing rules can be tested without standing up the
     * service's dependency graph.
     */
    static OptionalDouble monthlySalaryInMillions(String salaryText) {
        if (salaryText == null) {
            return OptionalDouble.empty();
        }
        String text = salaryText.toLowerCase();

        // Explicitly unquantified postings. "You'll love it" and friends carry no digits and
        // fall out below, but these say so in words and would otherwise match stray numbers.
        if (text.contains("thỏa thuận") || text.contains("thương lượng") || text.contains("negotiable")) {
            return OptionalDouble.empty();
        }

        String normalized = GROUP_SEPARATOR.matcher(text).replaceAll("");

        Matcher matcher = NUMBER.matcher(normalized);
        List<Double> numbers = new ArrayList<>();
        while (matcher.find()) {
            numbers.add(Double.parseDouble(matcher.group()));
        }
        if (numbers.isEmpty()) {
            return OptionalDouble.empty();
        }

        // A range ("10 - 20 triệu") averages to its midpoint; a single figure is itself.
        double average = numbers.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        if (average <= 0) {
            return OptionalDouble.empty();
        }

        // Dong wins when both appear: the scraper used to append the board's `currency`
        // field on top of amounts already written in dong, so a row can still read
        // "50.000.000đ USD" or "30M - UP TO 50M USD". Trusting the trailing USD there
        // converts a 30-50 million role as if it were dollars.
        boolean quotedInVnd = normalized.contains("vnd") || normalized.contains("vnđ")
                || normalized.contains("triệu") || normalized.contains("đồng")
                || DIGIT_VND_UNIT.matcher(normalized).find();
        boolean quotedInUsd = normalized.contains("usd") || normalized.contains("$");

        if (quotedInUsd && !quotedInVnd) {
            return OptionalDouble.of(average * USD_TO_VND / 1_000_000d);
        }
        // Whole dong ("15000000 vnd") rather than the millions everyone writes by hand.
        if (average >= 1_000_000d) {
            return OptionalDouble.of(average / 1_000_000d);
        }
        return OptionalDouble.of(average);
    }

    /** Hard cap, so a skill with 262 postings cannot be asked to ship 262 rows. */
    private static final int MAX_POSTINGS = 50;

    @Override
    @Transactional(readOnly = true)
    public SkillPostingsResponse getPostingsForSkill(UUID skillId, int limit) {
        if (skillId == null) {
            return SkillPostingsResponse.builder().totalCount(0).build();
        }
        int capped = Math.max(1, Math.min(limit, MAX_POSTINGS));
        String skillName = skillRepository.findById(skillId)
                .map(Skill::getSkillName)
                .orElse(null);

        List<PostingResponse> postings =
                recruitmentRepository.findPostingsForSkill(skillId, capped).stream()
                        .map(this::toPosting)
                        .toList();

        return SkillPostingsResponse.builder()
                .skillName(skillName)
                // The true total, not postings.size(). A list of 50 under a headline
                // of 158 has to say which one it is, or the cap reads as the count
                // and quietly contradicts the number the student clicked on.
                .totalCount(recruitmentRepository.countPostingsForSkill(skillId))
                .postings(postings)
                .build();
    }

    /** Positional read of the native projection, in the order the query selects. */
    private PostingResponse toPosting(Object[] row) {
        return PostingResponse.builder()
                .id(text(row, 0))
                .title(text(row, 1))
                .location(text(row, 2))
                .salary(text(row, 3))
                .experience(text(row, 4))
                .link(text(row, 5))
                .postedDate(text(row, 6))
                .seniority(text(row, 7))
                .build();
    }

    private String text(Object[] row, int index) {
        Object value = row == null || index >= row.length ? null : row[index];
        return value == null ? null : String.valueOf(value);
    }
}
