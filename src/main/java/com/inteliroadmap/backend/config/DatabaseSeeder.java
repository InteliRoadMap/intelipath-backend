package com.inteliroadmap.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.domain.enums.*;
import com.inteliroadmap.backend.repositories.AcademicCounselorRepository;
import com.inteliroadmap.backend.repositories.CareerRequiredSkillRepository;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.FeedbackRepository;
import com.inteliroadmap.backend.repositories.NodeTypeRepository;
import com.inteliroadmap.backend.repositories.SkillNodeRepository;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.repositories.StudentProgressRepository;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import com.inteliroadmap.backend.repositories.UniversityRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final SkillRepository skillRepository;
    private final CareerRoleRepository careerRoleRepository;
    private final SkillNodeRepository skillNodeRepository;
    private final CareerRequiredSkillRepository careerRequiredSkillRepository;
    private final AcademicCounselorRepository academicCounselorRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final FeedbackRepository feedbackRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final NodeTypeRepository nodeTypeRepository;
    private final UniversityRepository universityRepository;

    private static final String ROADMAP_TEMPLATE = "data/RoadmapDataTemplate.csv";
    private static final String SKILL_TEMPLATE = "data/SkillDataTemplate.csv";
    private static final String CAREER_TEMPLATE = "data/CareerDataTemplate.csv";
    private static final String UNIVERSITY_TEMPLATE = "data/VNUniversityDataTemplate.csv";

    @Override
    @Transactional
    public void run(String... args) {
        log.info("DatabaseSeeder: =====================================================");
        log.info("DatabaseSeeder:  CHECKING DATABASE SEED DATA... ");

        importUniversityData();
        importCareerData();
        importSkillData();
        importRoadmapData();
        importMockUsersData();

        log.info("DatabaseSeeder: =====================================================");
        log.info("DatabaseSeeder:  SEEDING SUMMARY NOTIFICATION ");
        log.info("DatabaseSeeder:  - Universities loaded: {}", universityRepository.count());
        log.info("DatabaseSeeder:  - Career Roles loaded: {}", careerRoleRepository.count());
        log.info("DatabaseSeeder:  - Skills loaded: {}", skillRepository.count());
        log.info("DatabaseSeeder:  - Career Required Skills loaded: {}", careerRequiredSkillRepository.count());
        log.info("DatabaseSeeder:  - Skill Nodes loaded: {}", skillNodeRepository.count());
        log.info("DatabaseSeeder:  - Mock Students loaded: {}", studentRepository.count());
        log.info("DatabaseSeeder: =====================================================");
    }

    private void importUniversityData() {
        File universityDataFile = new File(UNIVERSITY_TEMPLATE);
        if (!universityDataFile.exists()) {
            log.warn("DatabaseSeeder: {} not found. Skipping university import.", UNIVERSITY_TEMPLATE);
            return;
        }

        log.info("DatabaseSeeder: Starting CSV Import for Universities...");
        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new FileInputStream(universityDataFile), StandardCharsets.UTF_8))) {
            String[] line;
            int rowNum = 0;
            int imported = 0;

            while ((line = reader.readNext()) != null) {
                rowNum++;
                if (rowNum <= 1) continue;
                if (line.length < 2) continue;

                String code = line[0] == null ? "" : line[0].trim();
                String name = line[1] == null ? "" : line[1].trim();
                if (code.isEmpty() || name.isEmpty()) continue;

                University university = universityRepository.findByCode(code)
                        .orElseGet(() -> University.builder().code(code).build());
                university.setName(name);
                universityRepository.save(university);
                imported++;
            }

            log.info("DatabaseSeeder: Universities import completed. Processed {} rows.", imported);
        } catch (Exception e) {
            log.error("DatabaseSeeder: Error occurred while importing {}", UNIVERSITY_TEMPLATE, e);
        }
    }

    private void importCareerData() {
        // ----------------------------- IMPORT CAREER DATA ----------------------------- //
        File careerDataFile = new File(CAREER_TEMPLATE);
        if (!careerDataFile.exists()) {
            log.warn("DatabaseSeeder: CareerDataTemplate.csv not found. Skipping import.");
            return;
        }

        log.info("DatabaseSeeder: Starting CSV Import for Career Roles...");
        try (CSVReader reader = new CSVReader(new FileReader(careerDataFile))) {
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

                List<CareerRole> prerequisites = new ArrayList<>();
                String[] roles = prerequisite.split(",");
                for (String role : roles) {
                    prerequisites.add(careerRoleRepository.findByCareerName(role.trim()));
                }

                CareerRole careerRole = careerRoleRepository.findByCareerName(careerName);
                if (careerRole == null) {
                    careerRole = CareerRole.builder()
                            .careerName(careerName)
                            .prerequisite(prerequisites)
                            .description(description)
                            .build();
                } else {
                    careerRole.setPrerequisite(prerequisites);
                    careerRole.setDescription(description);
                }
                careerRoleRepository.save(careerRole);
            }
            log.info("DatabaseSeeder: CareerDataTemplate.csv data imported successfully.");
        } catch (Exception e) {
            log.error("DatabaseSeeder: Error occurred while importing CareerDataTemplate.csv", e);
        }
    }

    private void importSkillData() {
        // ------------------------------ IMPORT SKILL DATA ----------------------------- //
        File skillDataFile = new File(SKILL_TEMPLATE);
        if (!skillDataFile.exists()) {
            log.warn("DatabaseSeeder: SkillDataTemplate.csv not found. Skipping import.");
            return;
        }

        log.info("DatabaseSeeder: Starting CSV Import for Skill...");
        try (CSVReader reader = new CSVReader(new FileReader(skillDataFile))) {
            String[] line;
            int rowNum = 0;

            while ((line = reader.readNext()) != null) {
                rowNum++;
                // Skip the header rows
                if (rowNum <= 1) continue;
                if (line.length < 4) continue;

                String category = line[0];
                String careerRequired = line[1];
                String skillName = line[2];
                String importanceLevel = line[3];

                List<CareerRole> required = new ArrayList<>();
                String[] careers = careerRequired.split(",");
                for(String career: careers) {
                    required.add(careerRoleRepository.findByCareerName(career.trim()));
                }

                Skill skill = skillRepository.findBySkillName(skillName);
                if (skill == null) {
                    skill = Skill.builder()
                            .category(category)
                            .careers(required)
                            .skillName(skillName)
                            .build();
                } else {
                    skill.setCategory(category);
                    skill.setCareers(required);
                }
                skill = skillRepository.save(skill);

                for(String career: careers) {
                    CareerRole role = careerRoleRepository.findByCareerName(career.trim());
                    if (role != null) {
                        // Skip mappings that already exist so re-running the seeder
                        // (every app restart) does not violate uq_career_skill.
                        boolean mappingExists = careerRequiredSkillRepository
                                .existsByCareerRole_CareerIdAndSkill_SkillId(role.getCareerId(), skill.getSkillId());
                        if (mappingExists) continue;

                        CareerRequiredSkill careerRequiredSkill = CareerRequiredSkill.builder()
                                .careerRole(role)
                                .skill(skill)
                                .importanceLevel(ImportanceLevel.valueOf(importanceLevel.toUpperCase()))
                                .build();
                        careerRequiredSkillRepository.save(careerRequiredSkill);
                    } else {
                        log.warn("DatabaseSeeder: Career role '{}' not found for skill '{}'. Skipping required skill mapping.", career, skillName);
                    }
                }
            }
            log.info("DatabaseSeeder: SkillDataTemplate.csv data imported successfully.");
        } catch (Exception e) {
            log.error("DatabaseSeeder: Error occurred while importing SkillDataTemplate.csv", e);
        }
    }

    private void importRoadmapData() {
        // ----------------------------- IMPORT ROADMAP DATA ---------------------------- //
        File roadmapDataFile = new File(ROADMAP_TEMPLATE);
        if (!roadmapDataFile.exists()) {
            log.warn("DatabaseSeeder: RoadmapDataTemplate.csv not found. Skipping import.");
            return;
        }

        // Rows have no natural unique key, so re-importing a career would duplicate
        // its node tree. Seed per-career: skip any career that already has nodes,
        // but still import careers whose roadmap hasn't been seeded yet (e.g. adding
        // Backend/Full Stack after Frontend already exists).
        java.util.Set<java.util.UUID> careersAlreadySeeded = new java.util.HashSet<>();
        for (SkillNode existing : skillNodeRepository.findAll()) {
            if (existing.getCareerRole() != null && existing.getCareerRole().getCareerId() != null) {
                careersAlreadySeeded.add(existing.getCareerRole().getCareerId());
            }
        }

        log.info("DatabaseSeeder: Starting CSV Import for Roadmap Nodes...");
        try (CSVReader reader = new CSVReader(new FileReader(roadmapDataFile))) {
            String[] line;
            int rowNum = 0;

            // Node names repeat across stages (e.g. "React" under CORE and again under
            // ADVANCED), so a DB lookup by name is ambiguous. Resolve previous/parent
            // references against the most recently imported node with that name instead.
            Map<String, SkillNode> importedNodesByName = new HashMap<>();

            while ((line = reader.readNext()) != null) {
                rowNum++;
                // Skip the header row
                if (rowNum <= 1) continue;
                if (line.length < 13) continue;

                String careerName = line[0];
                CareerRole career = careerRoleRepository.findByCareerName(careerName);
                // Skip import Roadmap nodes for non-existing career
                if (career == null) continue;
                // Skip careers that were already seeded in a previous run.
                if (careersAlreadySeeded.contains(career.getCareerId())) continue;

                String skillName = line[1];
                String stageName = line[2];
                String unlockKey = line[3];
                int weight = (line[4] == null || line[4].isEmpty()) ? 0 : Integer.parseInt(line[4]);
                String completionPolicy = line[5];
                int reqProficiency = (line[6] == null || line[6].isEmpty()) ? 0 : Integer.parseInt(line[6]);
                String keywords = line[7];
                String previousNodeName = line[8];
                String parentNodeName = line[9];
                String nodeName = line[10];
                int nodeLevel = (line[11] == null || line[11].isEmpty()) ? 0 : Integer.parseInt(line[11]);
                String description = line[12];
                String link1 = line.length > 13 ? line[13] : "";
                String link2 = line.length > 14 ? line[14] : "";
                String link3 = line.length > 15 ? line[15] : "";

                ObjectMapper mapper = new ObjectMapper();
                ArrayNode links = mapper.createArrayNode();
                if (!link1.isEmpty()) links.add(link1);
                if (!link2.isEmpty()) links.add(link2);
                if (!link3.isEmpty()) links.add(link3);

                List<String> stageUnlockKey = new ArrayList<>();
                boolean requiredKey = false;
                if (!unlockKey.isEmpty()) {
                    requiredKey = true;
                    String[] keys = unlockKey.split(",");
                    for (String key : keys) stageUnlockKey.add(key.trim());
                }

                ArrayNode evidenceKeywords = mapper.createArrayNode();
                if (!keywords.isEmpty()) {
                    String [] keys = keywords.split(",");
                    for (String key : keys) evidenceKeywords.add(key.trim());
                }

                Skill skill = !skillName.isEmpty() ? skillRepository.findBySkillName(skillName) : null;

                SkillNode previousNode = importedNodesByName.get(previousNodeName.trim());
                SkillNode parentNode = importedNodesByName.get(parentNodeName.trim());

                NodeType nodeType = NodeType.builder()
                        .stage(StageType.valueOf(stageName.toUpperCase()))
                        .unlockKeyRequired(requiredKey)
                        .stageUnlockKey(stageUnlockKey)
                        .weight(weight)
                        .build();
                nodeType = nodeTypeRepository.save(nodeType);

                SkillNode skillNode = SkillNode.builder()
                        .careerRole(career)
                        .skill(skill)
                        .type(nodeType)
                        .previousNode(previousNode)
                        .parentNode(parentNode)
                        .nodeName(nodeName)
                        .nodeLevel(nodeLevel)
                        .description(description)
                        .resource(links)
                        .completionPolicy(completionPolicy)
                        .requiredProficiency(reqProficiency)
                        .evidenceKeywords(evidenceKeywords)
                        .build();
                skillNode = skillNodeRepository.save(skillNode);
                importedNodesByName.put(nodeName.trim(), skillNode);
            }
            log.info("DatabaseSeeder: CSV Import for Roadmap completed successfully.");
        } catch (Exception e) {
            log.error("DatabaseSeeder: Error occurred while importing CSV of Roadmap", e);
        }
    }

    public void importMockUsersData(){
        log.info("DatabaseSeeder: Seeding Mock Data (Students, Counselors, Feedbacks, Progress)...");

        // ---------------------------- Import Admin Account ---------------------------- //
        User admin = userRepository.findByEmail("thanhhau2110@gmail.com");
        if (admin == null) {
            admin = User.builder().email("thanhhau2110@gmail.com").build();
        }
        admin.setFullName("Hau Admin");
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);

        // ------------------------------- Get University ------------------------------- //
        University uni = universityRepository.findByCode("FPTU").orElse(null);

        // -------------------------- Import Counselor Account -------------------------- //
        User userCou = userRepository.findByEmail("mainclone1@gmail.com");
        if (userCou == null) {
            userCou = User.builder().email("mainclone1@gmail.com").build();
        }
        userCou.setFullName("Hau Counselor");
        userCou.setRole(UserRole.COUNSELOR);
        userCou = userRepository.save(userCou);

        AcademicCounselor counselor = academicCounselorRepository.findByUserId(userCou.getUserId());
        if (counselor == null) {
            counselor = AcademicCounselor.builder().userId(userCou.getUserId()).build();
        }
        counselor.setUniversity(uni);
        counselor.setDepartment("Software Engineer");
        academicCounselorRepository.save(counselor);

        // -------------------------- Import Students Accounts -------------------------- //
        // ------------------ Specific Student Account ------------------ //
        User userSt = userRepository.findByEmail("mainclone2@gmail.com");
        if (userSt == null) {
            userSt = User.builder().email("mainclone2@gmail.com").build();
        }
        userSt.setFullName("Hau ST");
        userSt.setYob(LocalDate.now().minusYears(10));
        userSt.setRole(UserRole.STUDENT);
        userSt = userRepository.save(userSt);

        Student st = studentRepository.findByUserId(userSt.getUserId());
        if (st == null) {
            st = Student.builder().userId(userSt.getUserId()).build();
        }
        st.setUniversity(uni);
        st.setUniversityName(uni.getName());
        st.setCareerRole(careerRoleRepository.findByCareerName("Frontend"));
        st.setMajor("Software Engineer");
        studentRepository.save(st);

        // ------------------- Random Student Accounts ------------------ //
        int limit = 100;
        if (studentRepository.count() >= limit) {
            log.info("DatabaseSeeder: Mock data ({}+ students) already seeded. Skipping...", limit);
            return;
        }

        CareerRole frontend = careerRoleRepository.findByCareerName("Frontend");
        if (frontend == null) {
            log.warn("DatabaseSeeder: Cannot seed {} Frontend students: Frontend career not found.", limit);
            return;
        }

        List<CareerRequiredSkill> feRequiredSkills = careerRequiredSkillRepository.findByCareerRole_CareerId(frontend.getCareerId());
        List<Skill> frontendSkills = feRequiredSkills.stream().map(CareerRequiredSkill::getSkill).toList();
        List<SkillNode> frontendNodes = skillNodeRepository.findByCareerRole_CareerId(frontend.getCareerId());

        Random random = new Random();

        log.info("DatabaseSeeder: Generating {} Frontend mock students...", limit);

        for (int i = 1; i <= limit; i++) {
            User sUser = User.builder()
                    .email("fe_student" + i + "@example.com")
                    .fullName("Frontend Student " + i)
                    .role(UserRole.STUDENT)
                    .build();
            sUser = userRepository.save(sUser);

            Student stu = Student.builder()
                    .userId(sUser.getUserId())
                    .careerRole(frontend)
                    .university(uni)
                    .universityName(uni.getName())
                    .yearOfAdmission(LocalDate.now().getYear() - random.nextInt(4))
                    .major("Software Engineer")
                    .build();
            stu = studentRepository.save(stu);

            // Assign random skills from Frontend
            if (!frontendSkills.isEmpty()) {
                int numSkills = 2 + random.nextInt(Math.min(10, frontendSkills.size()));
                Set<Skill> assignedSkills = new HashSet<>();
                for (int j = 0; j < numSkills; j++) {
                    Skill randomSkill = frontendSkills.get(random.nextInt(frontendSkills.size()));
                    if (assignedSkills.add(randomSkill)) {
                        StudentSkill ss = StudentSkill.builder()
                                .student(stu)
                                .skill(randomSkill)
                                .build();
                        studentSkillRepository.save(ss);
                    }
                }
            }

            // Roadmap Progress
            if (!frontendNodes.isEmpty() && random.nextBoolean()) {
                int numProgress = 1 + random.nextInt(Math.min(15, frontendNodes.size()));
                Set<UUID> addedProgressNodes = new HashSet<>();
                for (int k = 0; k < numProgress; k++) {
                    SkillNode node = frontendNodes.get(random.nextInt(frontendNodes.size()));
                    if (addedProgressNodes.add(node.getNodeId())) {
                        RoadmapStepStatus status = random.nextBoolean() ? RoadmapStepStatus.COMPLETED : RoadmapStepStatus.IN_PROGRESS;
                        StudentProgress progress = StudentProgress.builder()
                                .student(stu)
                                .skillNode(node)
                                .status(status)
                                .createdAt(LocalDateTime.now().minusDays(random.nextInt(30)))
                                .build();
                        if (status == RoadmapStepStatus.COMPLETED) {
                            progress.setCompletedAt(LocalDateTime.now().minusDays(random.nextInt(5)));
                        }
                        studentProgressRepository.save(progress);
                    }
                }
            }

            // Feedbacks to counselor
            FeedbackType[] types = FeedbackType.values();
            String[] counselorMessages = {
                    "You are doing a great job progressing on your Frontend roadmap. Keep it up!",
                    "I noticed you are missing some key Python skills. Consider taking a course on it.",
                    "Your recent test results look promising. Let's schedule a meeting to discuss next steps.",
                    "Please review the recommended study materials for Machine Learning."
            };

            // 1 counselor feedback to student
            if (random.nextBoolean()) {
                Feedback f1 = Feedback.builder()
                        .sender(userCou)
                        .receiver(sUser)
                        .content(counselorMessages[random.nextInt(counselorMessages.length)])
                        .type(types[random.nextInt(types.length)])
                        .createdAt(LocalDateTime.now().minusDays(random.nextInt(10)))
                        .updatedAt(LocalDateTime.now().minusDays(random.nextInt(10)))
                        .build();
                feedbackRepository.save(f1);
            }
        }
        log.info("DatabaseSeeder: Mock data seeding completed successfully.");
    }
}
