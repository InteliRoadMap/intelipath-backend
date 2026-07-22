package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.ai.client.AiServiceClient;
import com.inteliroadmap.backend.domain.entity.Recruitment;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.SkillTrend;
import com.inteliroadmap.backend.repositories.RecruitmentRepository;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.repositories.SkillTrendRepository;
import com.inteliroadmap.backend.services.SkillExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the {@link SkillExtractionService} interface.
 * Handles the extraction of skills from recruitment descriptions and rebuilds skill trends
 * utilizing an external AI service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillExtractionServiceImpl implements SkillExtractionService {

    private final RecruitmentRepository recruitmentRepository;
    private final SkillRepository skillRepository;
    private final SkillTrendRepository skillTrendRepository;
    private final AiServiceClient aiServiceClient;

    /**
     * Extracts skills from recruitment descriptions using an external AI service
     * and rebuilds the skill trends data in the database based on the extracted skills.
     * It groups the occurrences of each skill by the application deadline of the recruitment.
     */
    @Transactional
    @Override
    public void extractAndRebuildSkillTrends() {
        log.info("SkillExtractionServiceImpl: Starting Skill Trends extraction process via AI Service...");

        // 1. Get all Recruitment
        List<Recruitment> recruitments = recruitmentRepository.findAll();
        if (recruitments.isEmpty()) {
            log.info("SkillExtractionServiceImpl: No Recruitment data to process.");
            return;
        }

        // 2. Send batch request to AI Service
        List<String> descriptions = new ArrayList<>();
        List<LocalDate> dates = new ArrayList<>();

        for (Recruitment r : recruitments) {
            StringBuilder descBuilder = new StringBuilder();
            // Append title to the description
            String title = com.inteliroadmap.backend.mappers.ScraperMapper.str(r.getRecruitmentInfos(), "title");
            if (title != null) {
                descBuilder.append(title).append(". ");
            }
            // Append all description values
            if (r.getDescriptions() != null) {
                for (Object val : r.getDescriptions().values()) {
                    if (val instanceof java.util.List) {
                        for (Object v : (java.util.List<?>) val) {
                            descBuilder.append(v).append(" ");
                        }
                    } else if (val instanceof java.util.Map) {
                        for (Object v : ((java.util.Map<?, ?>) val).values()) {
                            if (v instanceof java.util.List) {
                                for (Object v2 : (java.util.List<?>) v) {
                                    descBuilder.append(v2).append(" ");
                                }
                            } else {
                                descBuilder.append(v).append(" ");
                            }
                        }
                    } else if (val != null) {
                        descBuilder.append(val).append(" ");
                    }
                }
            }
            
            // Clean up the description string and determine the relevant date.
            // Use the posting date so the trend reflects real demand-over-time;
            // fall back to the deadline, then today, when it's missing.
            String desc = descBuilder.toString().trim();
            LocalDate date = r.getPostedDate() != null ? r.getPostedDate()
                    : (r.getApplicationDeadline() != null ? r.getApplicationDeadline() : LocalDate.now());
            descriptions.add(desc);
            dates.add(date);
        }

        log.info("SkillExtractionServiceImpl: Sending {} descriptions to AI Service", descriptions.size());
        List<List<String>> extractedSkills = aiServiceClient.extractSkills(descriptions);
        if (extractedSkills.isEmpty()) {
            log.warn("SkillExtractionServiceImpl: AI Service returned no skills; nothing to rebuild.");
            return;
        }
        log.info("SkillExtractionServiceImpl: AI Service extraction completed successfully.");

        // 3. Group by (SkillName, Date) -> Count
        Map<String, Map<LocalDate, Integer>> skillDateCountMap = new HashMap<>();

        for (int i = 0; i < extractedSkills.size(); i++) {
            List<String> skills = extractedSkills.get(i);
            LocalDate date = dates.get(i);
            
            for (String skillName : skills) {
                skillDateCountMap.putIfAbsent(skillName, new HashMap<>());
                Map<LocalDate, Integer> dateCount = skillDateCountMap.get(skillName);
                dateCount.put(date, dateCount.getOrDefault(date, 0) + 1);
            }
        }

        // 4. Delete old SkillTrend data to rebuild
        skillTrendRepository.deleteAllInBatch();
        log.info("SkillExtractionServiceImpl: Cleared old SkillTrend data.");

        // 5. Save to Database
        List<SkillTrend> newTrends = new ArrayList<>();
        for (Map.Entry<String, Map<LocalDate, Integer>> entry : skillDateCountMap.entrySet()) {
            String skillName = entry.getKey();
            
            // Match the catalog without case sensitivity before minting anything. The
            // scraper's vocabulary is not the catalog's, so an exact-only lookup answered
            // "no such skill" for names that differ from a real entry by nothing a reader
            // would notice, and every miss inserted a new row: the catalog carries 300
            // skills of which 138 sit on no roadmap, including CSS beside CSS3 and Go
            // beside Golang.
            Skill skill = skillRepository.findBySkillNameIgnoreCase(skillName);
            if (skill == null) {
                // A genuinely new skill is still worth recording — market demand is exactly
                // where the catalog learns about things the roadmaps do not teach yet.
                skill = skillRepository.save(Skill.builder().skillName(skillName).build());
                log.info("SkillExtractionServiceImpl: '{}' is new to the catalog; added from market data.", skillName);
            }

            for (Map.Entry<LocalDate, Integer> dateEntry : entry.getValue().entrySet()) {
                SkillTrend trend = SkillTrend.builder()
                        .skill(Skill.builder().skillId(skill.getSkillId()).build())
                        .weekStamp(dateEntry.getKey())
                        .jobsNeeded(dateEntry.getValue())
                        .build();
                newTrends.add(trend);
            }
        }

        skillTrendRepository.saveAll(newTrends);
        log.info("SkillExtractionServiceImpl: Successfully saved {} new SkillTrend records to the database.", newTrends.size());
    }
}
