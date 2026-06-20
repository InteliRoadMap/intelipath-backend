package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.entity.Recruitment;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.SkillTrend;
import com.inteliroadmap.backend.repositories.RecruitmentRepository;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.repositories.SkillTrendRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillExtractionService {

    private final RecruitmentRepository recruitmentRepository;
    private final SkillRepository skillRepository;
    private final SkillTrendRepository skillTrendRepository;

    @Value("${ai.service.base-url:http://localhost:8000}")
    private String aiServiceBaseUrl;

    public record SkillExtractRequest(List<String> descriptions) {}
    public record SkillExtractResponse(List<List<String>> skills_per_doc) {}

    @Transactional
    public void extractAndRebuildSkillTrends() {
        log.info("Starting Skill Trends extraction process via AI Service...");

        // 1. Get all Recruitment
        List<Recruitment> recruitments = recruitmentRepository.findAll();
        if (recruitments.isEmpty()) {
            log.info("No Recruitment data to process.");
            return;
        }

        // 2. Send batch request to AI Service
        List<String> descriptions = new ArrayList<>();
        List<LocalDate> dates = new ArrayList<>();

        for (Recruitment r : recruitments) {
            StringBuilder descBuilder = new StringBuilder();
            if (r.getTitle() != null) {
                descBuilder.append(r.getTitle()).append(". ");
            }
            if (r.getDescriptions() != null) {
//                for (List<String> values : r.getDescriptions().values()) {
//                    if (values != null) {
//                        for (String v : values) {
//                            descBuilder.append(v).append(" ");
//                        }
//                    }
//                }
                for (Object valuesObj : r.getDescriptions().values()) {
                    if (valuesObj instanceof List<?> list) {
                        for (Object v : list) {
                            descBuilder.append(v.toString()).append(" ");
                        }
                    } else if (valuesObj instanceof String str) {
                        descBuilder.append(str).append(" ");
                    }
                }
            }
            
            String desc = descBuilder.toString().trim();
            LocalDate date = r.getApplicationDeadline() != null ? r.getApplicationDeadline() : LocalDate.now();
            descriptions.add(desc);
            dates.add(date);
        }

        RestTemplate restTemplate = new RestTemplate();
        String extractUrl = aiServiceBaseUrl + "/api/extract-skills";
        
        log.info("Sending {} descriptions to AI Service at: {}", descriptions.size(), extractUrl);
        ResponseEntity<SkillExtractResponse> response = restTemplate.postForEntity(
                extractUrl,
                new SkillExtractRequest(descriptions),
                SkillExtractResponse.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.error("Error occurred while calling AI Service.");
            return;
        }

        List<List<String>> extractedSkills = response.getBody().skills_per_doc();
        log.info("AI Service extraction completed successfully.");

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
        log.info("Cleared old SkillTrend data.");

        // 5. Save to Database
        List<SkillTrend> newTrends = new ArrayList<>();
        for (Map.Entry<String, Map<LocalDate, Integer>> entry : skillDateCountMap.entrySet()) {
            String skillName = entry.getKey();
            
            // Find or create new Skill in `skills` table
            Skill skill = skillRepository.findBySkillName(skillName);
            if (skill == null) {
                skill = Skill.builder()
                        .skillName(skillName)
                        .career("IT") // Default
                        .build();
                skill = skillRepository.save(skill);
            }

            for (Map.Entry<LocalDate, Integer> dateEntry : entry.getValue().entrySet()) {
                SkillTrend trend = SkillTrend.builder()
                        .skill(skill)
                        .weekStack(dateEntry.getKey())
                        .jobsNeeded(dateEntry.getValue())
                        .build();
                newTrends.add(trend);
            }
        }

        skillTrendRepository.saveAll(newTrends);
        log.info("Successfully saved {} new SkillTrend records to the database.", newTrends.size());
    }
}
