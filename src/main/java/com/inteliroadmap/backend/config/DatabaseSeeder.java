package com.inteliroadmap.backend.config;

import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.SkillNodeRepository;
import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final CareerRoleRepository careerRoleRepository;
    private final SkillNodeRepository skillNodeRepository;

    private static final String ROADMAP_TEMPLATE = "RoadmapDataTemplate.csv";

    @Override
    public void run(String... args) throws Exception {
        importRoadmapTemplate();
    }

    private void importRoadmapTemplate() {
        if (careerRoleRepository.count() > 0) {
            log.info("Roadmap already seeded. Skipping import.");
            return;
        }

        File csvFile = new File(ROADMAP_TEMPLATE);
        if (!csvFile.exists()) {
            log.warn("RoadmapTemplate.csv not found. Skipping import.");
            return;
        }

        log.info("Starting CSV Import for Roadmap Nodes...");
        try (CSVReader reader = new CSVReader(new FileReader(csvFile))) {
            String[] line;
            int rowNum = 0;
            
            java.util.Map<String, SkillNode> nodeMap = new HashMap<>();

            while ((line = reader.readNext()) != null) {
                rowNum++;
                // Skip the first two header rows
                if (rowNum <= 1) continue;

                if (line.length < 7) continue;

                String career = line[0];
                CareerRole existingRole = careerRoleRepository.findByCareerName(career);
                CareerRole careerRole;
                if (existingRole == null) {
                    careerRole = CareerRole.builder().careerName(career).build();
                    careerRole = careerRoleRepository.save(careerRole);
                } else {
                    careerRole = existingRole;
                }

                String prerequisite = line[1];
                String name = line[2];
                int level = Integer.parseInt(line[3]);
                int orderIndex = Integer.parseInt(line[4]);
                String resourceTitle = line[5];
                String description = line[6];
                String reference1 = line.length > 7 ? line[7] : "";
                String reference2 = line.length > 8 ? line[8] : "";
                String reference3 = line.length > 9 ? line[9] : "";

                Map<String, String> resourceItem = new HashMap<>();
                if (!resourceTitle.isEmpty()) resourceItem.put("title", resourceTitle);
                if (!description.isEmpty()) resourceItem.put("description", description);
                if (!reference1.isEmpty()) resourceItem.put("ref1", reference1);
                if (!reference2.isEmpty()) resourceItem.put("ref2", reference2);
                if (!reference3.isEmpty()) resourceItem.put("ref3", reference3);

                if (nodeMap.containsKey(name)) {
                    SkillNode existingNode = nodeMap.get(name);
                    java.util.List<Map<String, String>> resourcesList = (java.util.List<Map<String, String>>) existingNode.getResource();
                    resourcesList.add(resourceItem);
                    skillNodeRepository.save(existingNode);
                } else {
                    java.util.List<Map<String, String>> resourcesList = new java.util.ArrayList<>();
                    resourcesList.add(resourceItem);

                    SkillNode prereqNode = null;
                    if (!prerequisite.isEmpty()) {
                        prereqNode = nodeMap.get(prerequisite);
                        if (prereqNode == null) {
                            prereqNode = skillNodeRepository.findByName(prerequisite);
                        }
                    }

                    SkillNode skillNode = SkillNode.builder()
                            .careerRole(careerRole)
                            .prerequisite(prereqNode)
                            .name(name)
                            .level(level)
                            .orderIndex(orderIndex)
                            .description(null)
                            .resource(resourcesList)
                            .build();
                    
                    skillNode = skillNodeRepository.save(skillNode);
                    nodeMap.put(name, skillNode);
                }
            }
            log.info("CSV Import for Roadmap completed successfully.");
        } catch (Exception e) {
            log.error("Error occurred while importing CSV of Roadmap", e);
        }
    }
}
