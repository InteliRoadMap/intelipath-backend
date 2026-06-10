package com.inteliroadmap.backend.config;

import com.inteliroadmap.backend.domain.entity.*;
import com.opencsv.CSVReader;
import com.inteliroadmap.backend.repositories.*;
import com.inteliroadmap.backend.domain.enums.UserRole;
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
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final AcademicCounselorRepository academicCounselorRepository;
    private final FeedbackRepository feedbackRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final StudentProgressRepository studentProgressRepository;

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
        importMockStudentsAndFeedbacks();
        importMockFeedbacksForRealCounselors();
        
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

    private void importMockStudentsAndFeedbacks() {
        log.info("Starting Mock Data Import for Students...");

        List<AcademicCounselor> counselors = academicCounselorRepository.findAll();
        AcademicCounselor counselor;
        User counselorUser;
        if (counselors.isEmpty()) {
            counselorUser = userRepository.findByEmail("counselor_mock@example.com");
            if (counselorUser == null) {
                counselorUser = User.builder()
                        .email("counselor_mock@example.com")
                        .fullName("Mock Counselor")
                        .role(UserRole.COUNSELOR)
                        .build();
                counselorUser = userRepository.save(counselorUser);
            }
            counselor = AcademicCounselor.builder()
                    .userId(counselorUser.getUserId())
                    .university("InteliPath University")
                    .build();
            counselor = academicCounselorRepository.save(counselor);
        } else {
            counselor = counselors.get(0);
            counselorUser = userRepository.findById(counselor.getUserId()).orElse(null);
        }

        List<CareerRole> allCareers = careerRoleRepository.findAll();
        List<Skill> allSkills = skillRepository.findAll();
        List<SkillNode> allNodes = skillNodeRepository.findAll();

        if (allCareers.isEmpty() || allSkills.isEmpty()) return;

        java.util.Random random = new java.util.Random();

        for (int i = 1; i <= 20; i++) {
            String email = "student" + i + "@example.com";
            User user = userRepository.findByEmail(email);
            if (user == null) {
                user = User.builder()
                        .email(email)
                        .fullName("Student " + i)
                        .role(UserRole.STUDENT)
                        .build();
                user = userRepository.save(user);
            }

            if (studentRepository.findById(user.getUserId()).isPresent()) continue;

            CareerRole career = allCareers.get(random.nextInt(allCareers.size()));

            Student student = Student.builder()
                    .userId(user.getUserId())
                    .careerRole(career)
                    .university("Mock University " + (random.nextInt(5) + 1))
                    .build();
            student = studentRepository.save(student);

            java.util.Collections.shuffle(allSkills);
            int numSkills = random.nextInt(5) + 2;
            for (int j = 0; j < numSkills && j < allSkills.size(); j++) {
                Skill skill = allSkills.get(j);
                StudentSkill ss = StudentSkill.builder()
                        .student(student)
                        .skill(skill)
                        .build();
                studentSkillRepository.save(ss);
            }

            if (i % 3 == 0 && !allNodes.isEmpty()) {
                java.util.Collections.shuffle(allNodes);
                int numProgress = random.nextInt(4) + 1;
                for (int j = 0; j < numProgress && j < allNodes.size(); j++) {
                    SkillNode node = allNodes.get(j);
                    StudentProgress sp = StudentProgress.builder()
                            .student(student)
                            .skillNode(node)
                            .status(random.nextBoolean() ? "COMPLETED" : "IN_PROGRESS")
                            .build();
                    studentProgressRepository.save(sp);
                }
            }

            if (counselorUser != null) {
                if (random.nextBoolean()) {
                    Feedback f1 = Feedback.builder()
                            .sender(user)
                            .receiver(counselorUser)
                            .senderName(user.getFullName())
                            .content("Hello Counselor, I need help with my career path.")
                            .type("GENERAL")
                            .build();
                    feedbackRepository.save(f1);
                }
                if (random.nextBoolean()) {
                    Feedback f2 = Feedback.builder()
                            .sender(counselorUser)
                            .receiver(user)
                            .senderName(counselorUser.getFullName())
                            .content("Sure, " + user.getFullName() + ", let's schedule a meeting.")
                            .type("CAREER")
                            .build();
                    feedbackRepository.save(f2);
                }
            }
        }
        log.info("Mock Data Import completed.");
    }

    private void importMockFeedbacksForRealCounselors() {
        log.info("Checking for feedback seeding for real counselors...");
        List<User> counselors = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.COUNSELOR && !u.getEmail().equals("counselor_mock@example.com"))
                .collect(java.util.stream.Collectors.toList());

        if (counselors.isEmpty()) return;

        List<User> students = new java.util.ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            User st = userRepository.findByEmail("student" + i + "@example.com");
            if (st != null) students.add(st);
        }

        if (students.isEmpty()) return;

        java.util.Random random = new java.util.Random();

        for (User counselor : counselors) {
            List<Feedback> existing = feedbackRepository.findByReceiver(counselor);
            if (!existing.isEmpty()) continue;

            log.info("Seeding feedbacks for counselor: {}", counselor.getEmail());

            for (User student : students) {
                if (random.nextBoolean()) {
                    Feedback f1 = Feedback.builder()
                            .sender(student)
                            .receiver(counselor)
                            .senderName(student.getFullName())
                            .content("Hello " + counselor.getFullName() + ", I need help with my career path.")
                            .type("GENERAL")
                            .build();
                    feedbackRepository.save(f1);
                }
                if (random.nextBoolean()) {
                    Feedback f2 = Feedback.builder()
                            .sender(counselor)
                            .receiver(student)
                            .senderName(counselor.getFullName())
                            .content("Sure, " + student.getFullName() + ", let's schedule a meeting.")
                            .type("CAREER")
                            .build();
                    feedbackRepository.save(f2);
                }
            }
        }
        log.info("Feedback seeding for real counselors completed.");
    }
}
