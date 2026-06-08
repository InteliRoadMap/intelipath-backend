package com.inteliroadmap.backend.config;

import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.SkillNodeRepository;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final CareerRoleRepository careerRoleRepository;
    private final SkillNodeRepository skillNodeRepository;
    private final SkillRepository skillRepository;
    private final CareerRequiredSkillRepository careerRequiredSkillRepository;

    private static final String ROADMAP_TEMPLATE = "data/RoadmapDataTemplate.csv";
    private static final String SKILL_TEMPLATE = "data/SkillDataTemplate.csv";
    private static final String CAREER_TEMPLATE = "data/CareerDataTemplate.csv";

    @Override
    public void run(String... args) throws Exception {
        log.info("=====================================================");
        log.info(" CHECKING DATABASE SEED DATA... ");
        
        importCareerDataTemplate();
        importRoadmapDataTemplate();
        importSkillDataTemplate();
        
        log.info("=====================================================");
        log.info(" SEEDING SUMMARY NOTIFICATION ");
        log.info(" - Career Roles loaded: {}", careerRoleRepository.count());
        log.info(" - Skills loaded: {}", skillRepository.count());
        log.info(" - Career Required Skills loaded: {}", careerRequiredSkillRepository.count());
        log.info(" - Skill Nodes loaded: {}", skillNodeRepository.count());
        log.info("=====================================================");
    }

    private void importRoadmapDataTemplate() {
        if (skillNodeRepository.count() > 0) {
            log.info("Roadmap already seeded. Skipping import.");
            return;
        }

        File csvFile = new File(ROADMAP_TEMPLATE);
        if (!csvFile.exists()) {
            log.warn("RoadmapDataTemplate.csv not found. Skipping import.");
            return;
        }

        log.info("Starting CSV Import for Roadmap Nodes...");
        try (CSVReader reader = new CSVReader(new FileReader(csvFile))) {
            String[] line;
            int rowNum = 0;

            Map<String, SkillNode> nodeMap = new HashMap<>();

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

                String subtreeName = line[1];
                String connectToName = line[2];
                String childNodeOfName = line[3];
                String nodeName = line[4];
                String levelStr = line[5];
                Integer level = levelStr.isEmpty() ? null : Integer.parseInt(levelStr);
                String description = line[6];
                String link1 = line.length > 7 ? line[7] : "";
                String link2 = line.length > 8 ? line[8] : "";
                String link3 = line.length > 9 ? line[9] : "";

                Map<String, String> resourceItem = new HashMap<>();
                if (!link1.isEmpty()) resourceItem.put("link1", link1);
                if (!link2.isEmpty()) resourceItem.put("link2", link2);
                if (!link3.isEmpty()) resourceItem.put("link3", link3);


                if (nodeMap.containsKey(nodeName)) {
                    SkillNode existingNode = nodeMap.get(nodeName);
                    List<Map<String, String>> resourcesList = (List<Map<String, String>>) existingNode.getResource();
                    resourcesList.add(resourceItem);
                    skillNodeRepository.save(existingNode);
                } else {
                    List<Map<String, String>> resourcesList = new ArrayList<>();
                    resourcesList.add(resourceItem);

                    SkillNode connectToNode = null;
                    if (!connectToName.isEmpty()) {
                        connectToNode = nodeMap.get(connectToName);
                        if (connectToNode == null) {
                            connectToNode = skillNodeRepository.findByNodeName(connectToName);
                        }
                    }

                    SkillNode childNodeOfNode = null;
                    if (!childNodeOfName.isEmpty()) {
                        childNodeOfNode = nodeMap.get(childNodeOfName);
                        if (childNodeOfNode == null) {
                            childNodeOfNode = skillNodeRepository.findByNodeName(childNodeOfName);
                        }
                    }

                    SkillNode skillNode = SkillNode.builder()
                            .careerRole(careerRole)
                            .subtreeName(subtreeName)
                            .connectTo(connectToNode)
                            .childNodeOf(childNodeOfNode)
                            .nodeName(nodeName)
                            .level(level)
                            .description(description)
                            .resource(resourcesList)
                            .build();

                    skillNode = skillNodeRepository.save(skillNode);
                    nodeMap.put(nodeName, skillNode);
                }
            }
            log.info("CSV Import for Roadmap completed successfully.");
        } catch (Exception e) {
            log.error("Error occurred while importing CSV of Roadmap", e);
        }
    }

    private void importSkillDataTemplate() {
        if (skillRepository.count() > 0) {
            log.info("Skill already seeded. Skipping import.");
            return;
        }

        File csvFile = new File(SKILL_TEMPLATE);
        if (!csvFile.exists()) {
            log.warn("SkillDataTemplate.csv not found. Skipping import.");
            return;
        }

        log.info("Starting CSV Import for Skill...");
        try (CSVReader reader = new CSVReader(new FileReader(csvFile))) {
            String[] line;
            int rowNum = 0;

            Map<String, SkillNode> skillMap = new HashMap<>();

            while ((line = reader.readNext()) != null) {
                rowNum++;
                // Skip the first two header rows
                if (rowNum <= 1) continue;

                if (line.length < 4) continue;

                String category = line[0];
                String careerRequired = line[1];
                String skillName = line[2];

                Skill skill = skillRepository.findBySkillName(skillName);
                if (skill == null) {
                    Skill newskill = Skill.builder()
                            .category(category)
                            .career(careerRequired)
                            .skillName(skillName)
                            .build();
                    skill = skillRepository.save(newskill);
                }

                String importanceLevel = line[3];

                CareerRole role = careerRoleRepository.findByCareerName(careerRequired);

                if (role != null) {
                    CareerRequiredSkill careerRequiredSkill = CareerRequiredSkill.builder()
                            .careerRole(role)
                            .skill(skill)
                            .importanceLevel(importanceLevel)
                            .build();
                    careerRequiredSkillRepository.save(careerRequiredSkill);
                } else {
                    log.warn("Career role '{}' not found for skill '{}'. Skipping required skill mapping.", careerRequired, skillName);
                }
            }
            log.info("CSV Import for Skill completed successfully.");
        } catch (Exception e) {
            log.error("Error occurred while importing CSV of Skill", e);
        }
    }

    private void importCareerDataTemplate() {
        if (careerRoleRepository.count() > 0) {
            log.info("Career Roles already seeded. Skipping import.");
            return;
        }

        File csvFile = new File(CAREER_TEMPLATE);
        if (!csvFile.exists()) {
            log.warn("CareerDataTemplate.csv not found. Skipping import.");
            return;
        }

        log.info("Starting CSV Import for Career Roles...");
        try (CSVReader reader = new CSVReader(new FileReader(csvFile))) {
            String[] line;
            int rowNum = 0;

            while ((line = reader.readNext()) != null) {
                rowNum++;
                // Skip the header row
                if (rowNum <= 1) continue;

                if (line.length < 1) continue;

                String careerName = line[0];
                String prerequisite = line.length > 1 ? line[1] : "";
                String description = line.length > 2 ? line[2] : "";

                CareerRole careerRole = careerRoleRepository.findByCareerName(careerName);
                if (careerRole == null) {
                    careerRole = CareerRole.builder()
                            .careerName(careerName)
                            .prerequisite(prerequisite)
                            .description(description)
                            .build();
                } else {
                    careerRole.setPrerequisite(prerequisite);
                    careerRole.setDescription(description);
                }
                careerRoleRepository.save(careerRole);
            }
            log.info("CSV Import for Career Roles completed successfully.");
        } catch (Exception e) {
            log.error("Error occurred while importing CSV of Career Roles", e);
        }
    }
}
