package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.response.market.SkillDemandResponse;
import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.SkillTrend;
import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
import com.inteliroadmap.backend.mappers.MarketDemandMapper;
import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.RecruitmentRepository;
import com.inteliroadmap.backend.repositories.RecruitmentSkillRepository;
import com.inteliroadmap.backend.repositories.SkillTrendRepository;
import com.inteliroadmap.backend.services.MarketDemandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of {@link MarketDemandService}.
 *
 * <p>Aggregates the trend rows {@code SkillExtractionServiceImpl} already writes
 * against the number of postings in the same window, then weighs each skill by
 * how much it distinguishes the career being viewed. Adds no scraping and no new
 * tables; the weighting and its justification live in {@link MarketDemandMapper}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketDemandServiceImpl implements MarketDemandService {

    private final SkillTrendRepository skillTrendRepository;
    private final RecruitmentRepository recruitmentRepository;
    private final RecruitmentSkillRepository recruitmentSkillRepository;
    private final CareerRequiredSkillRepository careerRequiredSkillRepository;
    private final CareerRoleRepository careerRoleRepository;
    private final MarketDemandMapper marketDemandMapper;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, SkillDemandResponse> demandBySkill(UUID careerId) {
        if (careerId == null) {
            return Map.of();
        }

        LocalDate from = LocalDate.now().minusDays(WINDOW_DAYS);

        long sampleSize = recruitmentRepository.countCareerPostingsSince(careerId, from);
        if (sampleSize <= 0) {
            log.debug("MarketDemandServiceImpl: no postings in the last {} days; "
                    + "returning no demand figures.", WINDOW_DAYS);
            return Map.of();
        }

        int careerCount = (int) careerRoleRepository.count();
        if (careerCount <= 0) {
            return Map.of();
        }

        // How this career grades each skill. Also the set of skills it is willing
        // to talk about at all: a skill with no row here has no weight and no
        // document frequency, so there is nothing to rank it by.
        Map<UUID, ImportanceLevel> importanceBySkillId = new HashMap<>();
        for (CareerRequiredSkill required : careerRequiredSkillRepository.findByCareerRole_CareerId(careerId)) {
            if (required.getSkill() != null && required.getSkill().getSkillId() != null) {
                importanceBySkillId.put(required.getSkill().getSkillId(), required.getImportanceLevel());
            }
        }
        if (importanceBySkillId.isEmpty()) {
            log.debug("MarketDemandServiceImpl: career {} has no catalog rows; no demand to report.", careerId);
            return Map.of();
        }

        Map<UUID, Integer> careersNamingBySkillId = careersNamingBySkillId();

        // Numerator and denominator now describe the exact same population:
        // distinct postings for this career inside this window.
        Map<UUID, Integer> jobCounts = new HashMap<>();
        for (Object[] row : recruitmentSkillRepository.demandForCareerSince(careerId, from)) {
            UUID skillId = (UUID) row[0];
            if (!importanceBySkillId.containsKey(skillId)) continue;
            jobCounts.put(skillId, ((Number) row[1]).intValue());
        }

        Map<UUID, SkillDemandResponse> out = new HashMap<>();
        for (Map.Entry<UUID, Integer> e : jobCounts.entrySet()) {
            SkillDemandResponse demand = marketDemandMapper.toSkillDemandResponse(
                    e.getValue(), sampleSize, WINDOW_DAYS,
                    importanceBySkillId.get(e.getKey()),
                    careersNamingBySkillId.getOrDefault(e.getKey(), 1),
                    careerCount);
            if (demand != null) {
                out.put(e.getKey(), demand);
            }
        }

        log.debug("MarketDemandServiceImpl: {} skills relevant to career {} over {} postings "
                + "in the last {} days.", out.size(), careerId, sampleSize, WINDOW_DAYS);
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, SkillDemandResponse> rawDemandBySkill() {
        LocalDate from = LocalDate.now().minusDays(WINDOW_DAYS);

        long sampleSize = recruitmentRepository.countByPostedDateGreaterThanEqual(from);
        if (sampleSize < MarketDemandMapper.MIN_SAMPLE) {
            // Below the sample floor a percentage is noise dressed as a
            // measurement, and the caller is better off drawing nothing.
            log.debug("MarketDemandServiceImpl: {} postings in the last {} days is under the "
                    + "{}-posting floor; reporting no raw demand.", sampleSize, WINDOW_DAYS,
                    MarketDemandMapper.MIN_SAMPLE);
            return Map.of();
        }

        Map<UUID, Integer> jobCounts = new HashMap<>();
        for (SkillTrend t : skillTrendRepository.findByWeekStampGreaterThanEqual(from)) {
            if (t.getSkill() == null || t.getSkill().getSkillId() == null) continue;
            jobCounts.merge(t.getSkill().getSkillId(),
                    t.getJobsNeeded() == null ? 0 : t.getJobsNeeded(), Integer::sum);
        }

        Map<UUID, SkillDemandResponse> out = new HashMap<>();
        jobCounts.forEach((skillId, jobCount) -> {
            if (jobCount <= 0) {
                return;     // absent, not zero: "never named" is not "named zero times"
            }
            double frequency = Math.min(1.0, (double) jobCount / sampleSize);
            out.put(skillId, SkillDemandResponse.builder()
                    .frequency(frequency)
                    .jobCount(jobCount)
                    .sampleSize((int) sampleSize)
                    .windowDays(WINDOW_DAYS)
                    .reason(marketDemandMapper.reasonFor(frequency, sampleSize, WINDOW_DAYS, 1, 1))
                    .build());
        });
        return out;
    }

    /** Document frequency per skill: how many careers name it. */
    private Map<UUID, Integer> careersNamingBySkillId() {
        Map<UUID, Integer> counts = new HashMap<>();
        for (Object[] row : careerRequiredSkillRepository.countCareersPerSkill()) {
            if (row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            counts.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }
}
