package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.entity.Recruitment;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.SkillTrend;
import com.inteliroadmap.backend.repositories.RecruitmentRepository;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.repositories.SkillTrendRepository;
import com.inteliroadmap.backend.services.SkillExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

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

    @Value("${ai.service.base-url:http://localhost:8000}")
    private String aiServiceBaseUrl;

    public record SkillExtractRequest(List<String> descriptions) {}
    public record SkillExtractResponse(List<List<String>> skills_per_doc) {}

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
            String title = r.getBasicInfo() != null ? (String) r.getBasicInfo().get("title") : null;
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
            
            // Clean up the description string and determine the relevant date
            String desc = descBuilder.toString().trim();
            LocalDate date = r.getApplicationDeadline() != null ? r.getApplicationDeadline() : LocalDate.now();
            descriptions.add(desc);
            dates.add(date);
        }

        RestTemplate restTemplate = new RestTemplate();
        String extractUrl = aiServiceBaseUrl + "/api/extract-skills";
        
        log.info("SkillExtractionServiceImpl: Sending {} descriptions to AI Service at: {}", descriptions.size(), extractUrl);
        ResponseEntity<SkillExtractResponse> response = restTemplate.postForEntity(
                extractUrl,
                new SkillExtractRequest(descriptions),
                SkillExtractResponse.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.error("SkillExtractionServiceImpl: Error occurred while calling AI Service.");
            return;
        }

        List<List<String>> extractedSkills = response.getBody().skills_per_doc();
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
            
            // Find or create new Skill in `skills` table
            Skill skill = skillRepository.findBySkillName(skillName);
            if (skill == null) {
                skill = Skill.builder()
                        .skillName(skillName)
                        // .career("IT") // Default
                        .build();
                skill = skillRepository.save(skill);
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
