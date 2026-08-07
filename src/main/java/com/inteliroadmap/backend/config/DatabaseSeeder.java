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
import com.inteliroadmap.backend.repositories.FptSubjectRepository;
import com.inteliroadmap.backend.repositories.FptSubjectSkillRepository;
import com.inteliroadmap.backend.repositories.IndustryMentorRepository;
import com.inteliroadmap.backend.repositories.NodeTypeRepository;
import com.inteliroadmap.backend.repositories.PortfolioReviewRequestRepository;
import com.inteliroadmap.backend.repositories.SkillNodeRepository;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.repositories.StudentProgressRepository;
import com.inteliroadmap.backend.repositories.StudentAssessmentRepository;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.services.FptOverlayImportService;
import com.inteliroadmap.backend.services.PortfolioSlugService;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.ICSVParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.*;

import org.springframework.transaction.annotation.Transactional;
import com.inteliroadmap.backend.components.SkillNameCanonicalizer;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private static final String SEEDED_COUNSELOR_EMAIL = "counselornguyen12345@gmail.com";
    private static final String SEEDED_MENTOR_EMAIL = "mentornguyen12345@gmail.com";
    private static final String LEGACY_COUNSELOR_EMAIL = "mainclone1@gmail.com";
    private static final String LEGACY_MENTOR_EMAIL = "heomapkh939732948@gmail.com";

    private final SkillRepository skillRepository;
    /**
     * The catalog's identity function. The seeder runs on every application start and
     * used to look skills up with findBySkillName - an exact, CASE-SENSITIVE compare -
     * so it was the widest of the three doors through which the catalog forked, and the
     * only one that re-opened after every restart. Merging the forks without closing it
     * would have re-created "Fast API" beside "FastAPI" the next time the app came up.
     */
    private final SkillNameCanonicalizer skillNameCanonicalizer;
    private final CareerRoleRepository careerRoleRepository;
    private final SkillNodeRepository skillNodeRepository;
    private final CareerRequiredSkillRepository careerRequiredSkillRepository;
    private final AcademicCounselorRepository academicCounselorRepository;
    private final IndustryMentorRepository industryMentorRepository;
    private final PortfolioReviewRequestRepository reviewRequestRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final FeedbackRepository feedbackRepository;
    private final StudentProgressRepository studentProgressRepository;
    private final StudentAssessmentRepository studentAssessmentRepository;
    private final NodeTypeRepository nodeTypeRepository;
    private final FptSubjectRepository fptSubjectRepository;
    private final FptSubjectSkillRepository fptSubjectSkillRepository;
    private final FptOverlayImportService fptOverlayImportService;
    private final PasswordEncoder passwordEncoder;
    private final PortfolioSlugService portfolioSlugService;
    private final SeedAccountsProperties seedAccounts;

    // v2 seed data: id-based tree + selection semantics (see intelipath-service/scripts/migrate_roadmap.py).
    // v3 adds a status column produced by scripts/merge_incoming.py: the pool holds far more
    // nodes than have been curated, and only graded ones may reach a student.
    // v4 adds scripts/assign_node_skills.py: skill_group is resolved against skills_v4.csv
    // instead of holding the node's parent group name, so SkillNode.skill actually links.
    // v5 fixes two faults in the grading that v4 shipped with: a spine heading
    // carrying a single link lost its GROUP exemption and took its whole subtree
    // down (258 headings, which is why Java disappeared), and every source
    // roadmap emptied its spine into the career's top level (backend held 623
    // roots drawn from 23 roadmaps). Each imported roadmap is now its own
    // sub-tree, hung under the node it belongs to where one exists.
    // v6 adds scripts/extract_prerequisites.py: the `prerequisite` column, filled
    // from the source roadmaps' own previous_id. It was empty in every row, which
    // left the ordering logic guessing from node_level and stage.
    private static final String ROADMAP_TEMPLATE = "data/v2/roadmap_nodes_v6.csv";
    private static final String ROADMAP_TEMPLATE_FALLBACK = "data/v2/roadmap_nodes.csv";

    /**
     * Node grades that are allowed into the database.
     *
     * READY carries a summary and the two resource links FR2.3 requires. CHECKPOINT nodes are
     * deliverables ("Checkpoint - Simple CRUD Apps") rather than reading material, and GROUP
     * nodes are the headings roadmap.sh draws as frames; demanding links of either is a
     * category error, and dropping them would orphan everything beneath.
     */
    private static final Set<String> PUBLISHABLE_STATUS = Set.of("READY", "CHECKPOINT", "GROUP");
    // v4 catalog: name, career_id, importance, category, sources — built by
    // scripts/build_skill_catalog.py from the scraper's market data, the
    // imported roadmap.sh nodes, and five classified third-party sources
    // (linguist, devicon, O*NET, Wikidata, StackOverflow tag synonyms).
    // v5 removes the 90 catalog entries that are not skills — "Introduction",
    // "Components", "Learn the Basics", "Pick a Framework". They came from
    // roadmap.sh section headings, were never confirmed by a job posting, and fed
    // straight into the assessment and the learning plan: a step titled
    // "Introduction" is not advice. See scripts/filter_skill_catalog.py.
    private static final String SKILL_TEMPLATE = "data/v2/skills_v5.csv";
    private static final String SKILL_TEMPLATE_FALLBACK = "data/v2/skills.csv";
    private static final String CAREER_TEMPLATE = "data/v2/careers.csv";
    private static final String FPT_UNIVERSITY_NAME = "FPT University";
    // FLM curriculum overlay (subjects + skill coverage + lesson resources).
    private static final String FLM_OVERLAY = "data/flm_overlay.json";

    // Resolves a roadmap row's career_id slug (e.g. "backend") to the persisted
    // CareerRole. Populated by importCareerData, consumed by importSkillData and
    // importRoadmapData.
    private final Map<String, CareerRole> careerBySlug = new HashMap<>();

    @Override
    @Transactional
    public void run(String... args) {
        log.info("DatabaseSeeder: =====================================================");
        log.info("DatabaseSeeder:  CHECKING DATABASE SEED DATA... ");

        importCareerData();
        importSkillData();
        importRoadmapData();
        importFptSubjectData();
        importMockUsersData();
        importMockReviewRequests();
        backfillPortfolioSlugs();

        log.info("DatabaseSeeder: =====================================================");
        log.info("DatabaseSeeder:  SEEDING SUMMARY NOTIFICATION ");
        log.info("DatabaseSeeder:  - Career Roles loaded: {}", careerRoleRepository.count());
        log.info("DatabaseSeeder:  - Skills loaded: {}", skillRepository.count());
        log.info("DatabaseSeeder:  - Career Required Skills loaded: {}", careerRequiredSkillRepository.count());
        log.info("DatabaseSeeder:  - Skill Nodes loaded: {}", skillNodeRepository.count());
        log.info("DatabaseSeeder:  - FPT Subjects loaded: {}", fptSubjectRepository.count());
        log.info("DatabaseSeeder:  - FPT Subject Skills loaded: {}", fptSubjectSkillRepository.count());
        log.info("DatabaseSeeder:  - Mock Students loaded: {}", studentRepository.count());
        log.info("DatabaseSeeder: =====================================================");
    }

    /**
     * Seed the FLM curriculum overlay (data/flm_overlay.json) into an empty database:
     * each subject plus the catalog skills it covers and its lesson resources. Reference
     * tables only — never touches student_fpt_subjects, so student declarations survive.
     *
     * Bootstrap only. The file is a stale snapshot (one curriculum, no CLOs, no files) and
     * importOverlay rebuilds a subject by clearing its skills/CLOs/resources first, so
     * re-running it over synced data DELETES what the admin sync fetched — a restart alone
     * was enough to cut a fresh sync's CLOs from 453 to 270 and its downloadable files from
     * 63 to 32. Once any FPT subject exists, the live sync owns this data and the file
     * must keep its hands off.
     */
    private void importFptSubjectData() {
        File overlayFile = new File(FLM_OVERLAY);
        if (!overlayFile.exists()) {
            log.warn("DatabaseSeeder: {} not found. Skipping FPT subject import.", FLM_OVERLAY);
            return;
        }

        long existing = fptSubjectRepository.count();
        if (existing > 0) {
            log.info("DatabaseSeeder: {} FPT subjects already present — leaving them to the FLM sync, "
                    + "skipping the {} snapshot.", existing, FLM_OVERLAY);
            return;
        }

        log.info("DatabaseSeeder: Starting FPT subject import from {}...", FLM_OVERLAY);
        try {
            JsonNode root = new ObjectMapper().readTree(overlayFile);
            // The offline seed is the SE technical curriculum (curid 2507). Register it as
            // the default curriculum so students whose cohort has no synced version fall back
            // to it.
            FptOverlayImportService.CurriculumRef ref =
                    new FptOverlayImportService.CurriculumRef("BIT_SE", "2507", true);
            FptOverlayImportService.ImportSummary summary = fptOverlayImportService.importOverlay(root, ref);
            log.info("DatabaseSeeder: FPT import done — {} subjects, {} skill links ({} unmatched names), "
                            + "{} CLOs, {} resources.",
                    summary.subjects(), summary.skillLinks(), summary.unmatchedSkills(),
                    summary.clos(), summary.resources());
        } catch (Exception e) {
            log.error("DatabaseSeeder: Error occurred while importing {}", FLM_OVERLAY, e);
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
        // v2 careers.csv columns: career_id (slug), name, prerequisites (name list), description.
        // Prerequisites reference other careers by name, so upsert every career first
        // (pass 1) and only then wire up the prerequisite links (pass 2).
        List<String[]> rows = new ArrayList<>();
        try (CSVReader reader = openSeedCsv(careerDataFile)) {
            String[] line;
            int rowNum = 0;
            while ((line = reader.readNext()) != null) {
                rowNum++;
                if (rowNum <= 1) continue; // header
                if (line.length < 2) continue;
                rows.add(line);
            }
        } catch (Exception e) {
            log.error("DatabaseSeeder: Error occurred while reading {}", CAREER_TEMPLATE, e);
            return;
        }

        // Pass 1: upsert careers and index them by slug for later imports.
        for (String[] line : rows) {
            String slug = line[0].trim();
            String careerName = line[1].trim();
            String description = line.length > 3 ? line[3] : "";

            CareerRole careerRole = careerRoleRepository.findByCareerName(careerName);
            if (careerRole == null) {
                careerRole = CareerRole.builder()
                        .careerName(careerName)
                        .description(description)
                        .build();
            } else {
                careerRole.setDescription(description);
            }
            careerRole = careerRoleRepository.save(careerRole);
            careerBySlug.put(slug, careerRole);
        }

        // Pass 2: resolve prerequisite career names now that all careers exist.
        for (String[] line : rows) {
            String prerequisite = line.length > 2 ? line[2] : "";
            if (prerequisite.isBlank()) continue;

            CareerRole careerRole = careerRoleRepository.findByCareerName(line[1].trim());
            if (careerRole == null) continue;

            List<CareerRole> prerequisites = new ArrayList<>();
            for (String role : prerequisite.split(",")) {
                CareerRole prereq = careerRoleRepository.findByCareerName(role.trim());
                if (prereq != null) prerequisites.add(prereq);
            }
            careerRole.setPrerequisite(prerequisites);
            careerRoleRepository.save(careerRole);
        }
        log.info("DatabaseSeeder: careers.csv data imported successfully ({} careers).", careerBySlug.size());
    }

    private void importSkillData() {
        // ------------------------------ IMPORT SKILL DATA ----------------------------- //
        File skillDataFile = new File(SKILL_TEMPLATE);
        if (!skillDataFile.exists()) {
            skillDataFile = new File(SKILL_TEMPLATE_FALLBACK);
            if (!skillDataFile.exists()) {
                log.warn("DatabaseSeeder: neither {} nor {} found. Skipping skill import.",
                        SKILL_TEMPLATE, SKILL_TEMPLATE_FALLBACK);
                return;
            }
            log.info("DatabaseSeeder: {} not found, falling back to {}.",
                    SKILL_TEMPLATE, SKILL_TEMPLATE_FALLBACK);
        }

        log.info("DatabaseSeeder: Starting CSV Import for Skill...");
        // Columns: name, career_id (slug), importance, [category]. category is v4-only —
        // the v2 fallback file has 3 columns, so it is read only when present. One row
        // per (skill, career): a skill shared by two careers appears twice. Keep a single
        // Skill entity per name and accumulate its careers across rows.
        try (CSVReader reader = openSeedCsv(skillDataFile)) {
            String[] line;
            int rowNum = 0;

            while ((line = reader.readNext()) != null) {
                rowNum++;
                // Skip the header row
                if (rowNum <= 1) continue;
                if (line.length < 3) continue;

                String skillName = line[0].trim();
                String careerSlug = line[1].trim();
                String importanceLevel = line[2].trim();
                String category = line.length > 3 ? line[3].trim() : null;
                if (skillName.isEmpty()) continue;

                CareerRole role = careerBySlug.get(careerSlug);
                if (role == null) {
                    log.warn("DatabaseSeeder: Unknown career slug '{}' for skill '{}'. Skipping.", careerSlug, skillName);
                    continue;
                }

                Skill skill = skillNameCanonicalizer.resolve(skillName);
                boolean newToCatalog = skill == null;
                if (skill == null) {
                    skill = Skill.builder()
                            .skillName(skillName)
                            .category(blankToNull(category))
                            .careers(new ArrayList<>(List.of(role)))
                            .build();
                } else {
                    if (skill.getCategory() == null && category != null && !category.isEmpty()) {
                        skill.setCategory(category);
                    }
                    if (skill.getCareers() == null || skill.getCareers().stream()
                            .noneMatch(c -> c.getCareerId().equals(role.getCareerId()))) {
                        List<CareerRole> careers = skill.getCareers() != null
                                ? new ArrayList<>(skill.getCareers()) : new ArrayList<>();
                        careers.add(role);
                        skill.setCareers(careers);
                    }
                }
                skill = skillRepository.save(skill);
                if (newToCatalog) {
                    // Keep the canonicaliser's index current without rebuilding it: the
                    // very next CSV row may be this skill's plural.
                    skillNameCanonicalizer.remember(skill);
                }

                // Skip mappings that already exist so re-running the seeder
                // (every app restart) does not violate uq_career_skill.
                boolean mappingExists = careerRequiredSkillRepository
                        .existsByCareerRole_CareerIdAndSkill_SkillId(role.getCareerId(), skill.getSkillId());
                if (mappingExists) continue;

                careerRequiredSkillRepository.save(CareerRequiredSkill.builder()
                        .careerRole(role)
                        .skill(skill)
                        .importanceLevel(parseImportance(importanceLevel))
                        .build());
            }
            log.info("DatabaseSeeder: skills.csv data imported successfully.");
        } catch (Exception e) {
            log.error("DatabaseSeeder: Error occurred while importing {}", SKILL_TEMPLATE, e);
        }
    }

    /** Maps a CSV importance token (HIGH/AVG/LOW) to the enum, defaulting to AVG. */
    private ImportanceLevel parseImportance(String raw) {
        if (raw == null || raw.isBlank()) {
            return ImportanceLevel.AVG;
        }
        try {
            return ImportanceLevel.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ImportanceLevel.AVG;
        }
    }

    // v2 roadmap_nodes.csv column indices.
    private static final int R_NODE_ID = 0, R_CAREER_ID = 1, R_SKILL_GROUP = 2, R_NAME = 3,
            R_NODE_LEVEL = 4, R_STAGE = 5, R_AXIS = 6, R_NODE_KIND = 7, R_IS_OPTIONAL = 8,
            R_IS_CHECKPOINT = 9, R_SELECTION = 10, R_CHOOSE_COUNT = 11, R_WEIGHT = 12,
            R_COMPLETION_POLICY = 13, R_REQUIRED_PROFICIENCY = 14, R_EVIDENCE_KEYWORDS = 15,
            R_PARENT_ID = 16, R_PREVIOUS_ID = 17, R_DESCRIPTION = 18, R_LINK1 = 19, R_LINK2 = 20,
            R_LINK3 = 21, R_STATUS = 22, R_PREREQUISITE = 23;

    /**
     * Reader for the seed CSVs, with OpenCSV's backslash escaping disabled.
     *
     * OpenCSV treats {@code \} as an escape character by default, which RFC 4180 does not:
     * a quoted field is ended only by its closing quote. The roadmap descriptions come from
     * upstream prose and contain backslashes (regex snippets, CLI examples), so the default
     * parser mis-reads those rows and then keeps consuming following rows until the quotes
     * happen to rebalance. Twenty-seven backslashes silently swallowed 915 nodes.
     */
    private static CSVReader openSeedCsv(File file) throws java.io.IOException {
        return new CSVReaderBuilder(new FileReader(file))
                .withCSVParser(new CSVParserBuilder()
                        .withEscapeChar(ICSVParser.NULL_CHARACTER)
                        .build())
                .build();
    }

    private void importRoadmapData() {
        // ----------------------------- IMPORT ROADMAP DATA ---------------------------- //
        File roadmapDataFile = new File(ROADMAP_TEMPLATE);
        if (!roadmapDataFile.exists()) {
            // A checkout without the graded file still seeds: the v2 file has no status
            // column, and an absent column reads as blank, which the filter lets through.
            roadmapDataFile = new File(ROADMAP_TEMPLATE_FALLBACK);
            if (!roadmapDataFile.exists()) {
                log.warn("DatabaseSeeder: neither {} nor {} found. Skipping roadmap import.",
                        ROADMAP_TEMPLATE, ROADMAP_TEMPLATE_FALLBACK);
                return;
            }
            log.info("DatabaseSeeder: {} not found, falling back to {}.",
                    ROADMAP_TEMPLATE, ROADMAP_TEMPLATE_FALLBACK);
        }

        // Rows have no natural unique key, so re-importing a career would duplicate
        // its node tree. Seed per-career: skip any career that already has nodes,
        // but still import careers whose roadmap hasn't been seeded yet (e.g. adding
        // Backend/Full Stack after Frontend already exists).
        Set<UUID> careersAlreadySeeded = new HashSet<>();
        for (SkillNode existing : skillNodeRepository.findAll()) {
            if (existing.getCareerRole() != null && existing.getCareerRole().getCareerId() != null) {
                careersAlreadySeeded.add(existing.getCareerRole().getCareerId());
            }
        }

        log.info("DatabaseSeeder: Starting CSV Import for Roadmap Nodes...");
        ObjectMapper mapper = new ObjectMapper();
        try (CSVReader reader = openSeedCsv(roadmapDataFile)) {
            // v2 refs are stable slug ids (parent_id/previous_id), which may point
            // forward in the file. Import in two passes: create every node first
            // (indexed by its slug), then wire up parent/previous edges.
            Map<String, SkillNode> nodesBySlug = new HashMap<>();
            List<String[]> edgeRows = new ArrayList<>();

            String[] line;
            int rowNum = 0;
            int skippedByStatus = 0, skippedByCareer = 0;
            while ((line = reader.readNext()) != null) {
                rowNum++;
                if (rowNum <= 1) continue; // header
                if (line.length <= R_PREVIOUS_ID) continue;

                String nodeSlug = cell(line, R_NODE_ID);
                if (nodeSlug.isEmpty()) continue;

                // Blank means the v2 file, which predates grading and is entirely curated.
                String status = cell(line, R_STATUS);
                if (!status.isEmpty() && !PUBLISHABLE_STATUS.contains(status)) {
                    skippedByStatus++;
                    continue;
                }

                CareerRole career = careerBySlug.get(cell(line, R_CAREER_ID));
                if (career == null) {
                    // The pool covers roadmaps beyond the careers this system offers
                    // (mobile, product, blockchain, ...). They stay out until a career exists.
                    skippedByCareer++;
                    continue;
                }
                if (careersAlreadySeeded.contains(career.getCareerId())) continue;

                ArrayNode links = mapper.createArrayNode();
                for (int idx : new int[]{R_LINK1, R_LINK2, R_LINK3}) {
                    String link = cell(line, idx);
                    if (!link.isEmpty()) links.add(link);
                }

                ArrayNode evidenceKeywords = mapper.createArrayNode();
                String keywords = cell(line, R_EVIDENCE_KEYWORDS);
                if (!keywords.isEmpty()) {
                    for (String key : keywords.split(",")) {
                        String trimmed = key.trim();
                        if (!trimmed.isEmpty()) evidenceKeywords.add(trimmed);
                    }
                }

                // What must come before this node, as the roadmap's own author
                // ordered it. Malformed JSON is dropped rather than failing the
                // import: a bad prerequisite costs one ordering hint, a failed
                // import costs the whole roadmap.
                JsonNode prerequisite = null;
                String prerequisiteJson = cell(line, R_PREREQUISITE);
                if (!prerequisiteJson.isEmpty()) {
                    try {
                        prerequisite = mapper.readTree(prerequisiteJson);
                    } catch (IOException e) {
                        log.warn("DatabaseSeeder: unreadable prerequisite on node '{}', skipping it: {}",
                                nodeSlug, e.getMessage());
                    }
                }

                String skillName = cell(line, R_SKILL_GROUP);
                Skill skill = skillName.isEmpty() ? null : skillNameCanonicalizer.resolve(skillName);
                boolean careerSkill = skill != null && careerRequiredSkillRepository
                        .existsByCareerRole_CareerIdAndSkill_SkillId(career.getCareerId(), skill.getSkillId());

                // Stage/weight still live on NodeType. The v2 schema no longer carries
                // stage-unlock keys, so gating is driven purely by parent/previous edges.
                NodeType nodeType = nodeTypeRepository.save(NodeType.builder()
                        .stage(parseStage(cell(line, R_STAGE)))
                        .unlockKeyRequired(false)
                        .stageUnlockKey(new ArrayList<>())
                        .weight(parseInt(cell(line, R_WEIGHT), 0))
                        .build());

                SkillNode skillNode = skillNodeRepository.save(SkillNode.builder()
                        .careerRole(career)
                        .skill(careerSkill ? skill : null)
                        .semanticType(careerSkill ? "SKILL" : "CAPABILITY")
                        .type(nodeType)
                        .nodeName(cell(line, R_NAME))
                        .nodeLevel(parseInt(cell(line, R_NODE_LEVEL), 0))
                        .description(cell(line, R_DESCRIPTION))
                        .resource(links)
                        .completionPolicy(blankToNull(cell(line, R_COMPLETION_POLICY)))
                        .requiredProficiency(parseNullableInt(cell(line, R_REQUIRED_PROFICIENCY)))
                        .evidenceKeywords(evidenceKeywords)
                        .prerequisite(prerequisite)
                        .selection(defaultTo(cell(line, R_SELECTION), "ALL"))
                        .chooseCount(parseNullableInt(cell(line, R_CHOOSE_COUNT)))
                        .nodeKind(defaultTo(cell(line, R_NODE_KIND), "CORE"))
                        .axis(defaultTo(cell(line, R_AXIS), "MAIN"))
                        .isOptional(parseBool(cell(line, R_IS_OPTIONAL)))
                        .isCheckpoint(parseBool(cell(line, R_IS_CHECKPOINT)))
                        .build());

                nodesBySlug.put(nodeSlug, skillNode);
                edgeRows.add(line);
            }

            Set<String> topicSlugs = edgeRows.stream()
                    .map(row -> cell(row, R_PARENT_ID))
                    .filter(value -> !value.isEmpty())
                    .collect(java.util.stream.Collectors.toSet());

            // Pass 2: resolve edges and persist the semantic contract. Topic status
            // comes from actual parent references, never name/depth heuristics.
            for (String[] row : edgeRows) {
                SkillNode node = nodesBySlug.get(cell(row, R_NODE_ID));
                if (node == null) continue;
                SkillNode parent = nodesBySlug.get(cell(row, R_PARENT_ID));
                SkillNode previous = nodesBySlug.get(cell(row, R_PREVIOUS_ID));
                node.setParentNode(parent);
                node.setPreviousNode(previous);
                if (Boolean.TRUE.equals(node.getIsCheckpoint())) {
                    node.setSemanticType("CHECKPOINT");
                    node.setSkill(null);
                    node.setRequiredProficiency(null);
                } else if (topicSlugs.contains(cell(row, R_NODE_ID))) {
                    node.setSemanticType("TOPIC");
                    node.setSkill(null);
                    node.setRequiredProficiency(null);
                }
                skillNodeRepository.save(node);
            }

            log.info("DatabaseSeeder: {} import completed ({} nodes seeded; "
                            + "{} skipped as ungraded, {} skipped for an unknown career).",
                    roadmapDataFile.getName(), nodesBySlug.size(), skippedByStatus, skippedByCareer);
        } catch (Exception e) {
            log.error("DatabaseSeeder: Error occurred while importing {}", ROADMAP_TEMPLATE, e);
        }
    }

    /** Safe cell accessor: trimmed value or "" when the column is missing/null. */
    private String cell(String[] row, int index) {
        if (row == null || index >= row.length || row[index] == null) {
            return "";
        }
        return row[index].trim();
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private String defaultTo(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private int parseInt(String value, int fallback) {
        try {
            return (value == null || value.isBlank()) ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private Integer parseNullableInt(String value) {
        try {
            return (value == null || value.isBlank()) ? null : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** CSV holds "true"/"false"/"" for boolean flags; empty means false. */
    private boolean parseBool(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    /** Parses a stage token to StageType, defaulting to FOUNDATION on unknown/empty. */
    private StageType parseStage(String value) {
        if (value == null || value.isBlank()) {
            return StageType.FOUNDATION;
        }
        try {
            return StageType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("DatabaseSeeder: Unknown stage '{}', defaulting to FOUNDATION.", value);
            return StageType.FOUNDATION;
        }
    }

    /**
     * Applies a seeded account's local credential from configuration.
     *
     * When the pair is absent the account keeps whatever it already had, so an
     * unconfigured environment simply leaves it OAuth-only rather than seeding a
     * password that everyone reading the repo would know.
     *
     * @param user the account being seeded
     * @param account the configured username/password pair
     * @param role label used in the skip log
     */
    private void applySeedCredentials(User user, SeedAccountsProperties.Account account, String role) {
        if (!account.isUsable()) {
            log.info("DatabaseSeeder: No seed credential configured for the {} account; leaving it OAuth-only.", role);
            return;
        }
        user.setUsername(account.getUsername());
        user.setPasswordHash(passwordEncoder.encode(account.getPassword()));
    }

    /**
     * Gives a slug to any student still missing one.
     *
     * The DDL declares portfolio_slug NOT NULL, but that only binds databases
     * created after the change: 01_init_intelipath.sql runs once, on an empty
     * volume. Databases seeded before it hold rows with a null slug, and every
     * one of them is a portfolio nobody can open. This repairs them in place.
     *
     * Cheap when there is nothing to do — one indexed query returning no rows.
     */
    private void backfillPortfolioSlugs() {
        List<Student> unslugged = studentRepository.findByPortfolioSlugIsNullOrPortfolioSlugEquals("");
        if (unslugged.isEmpty()) {
            return;
        }

        log.info("DatabaseSeeder: Backfilling portfolio slugs for {} student(s)...", unslugged.size());
        for (Student student : unslugged) {
            User user = userRepository.findById(student.getUserId()).orElse(null);
            if (user == null) {
                log.warn("DatabaseSeeder: Student {} has no user row; cannot build a slug.", student.getUserId());
                continue;
            }
            student.setPortfolioSlug(portfolioSlugService.allocateFor(user));
            studentRepository.save(student);
        }
        log.info("DatabaseSeeder: Portfolio slug backfill done.");
    }

    /**
     * Portfolio review requests aimed at the seeded mentor.
     *
     * This is the only relationship between a mentor and a student that exists:
     * every mentor-side number — "mentees", pending reviews, the student list —
     * is derived from this table. With none, the mentor dashboard is honestly
     * empty and there is nothing to look at in dev.
     *
     * Deliberately its own method rather than a few lines appended to
     * importMockUsersData(): that method returns early once 100 students exist,
     * so anything after that point would never run on an already-seeded database.
     */
    private void importMockReviewRequests() {
        if (reviewRequestRepository.count() > 0) {
            log.info("DatabaseSeeder: Review requests already seeded. Skipping...");
            return;
        }

        User mentor = userRepository.findByEmail(SEEDED_MENTOR_EMAIL);
        if (mentor == null) {
            log.warn("DatabaseSeeder: Cannot seed review requests: no seeded mentor found.");
            return;
        }

        // Oldest first, so the queue has a visible spread of waiting times rather
        // than five requests that all arrived in the same second.
        List<User> students = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.STUDENT)
                .limit(5)
                .toList();

        if (students.isEmpty()) {
            log.warn("DatabaseSeeder: Cannot seed review requests: no students found.");
            return;
        }

        int daysAgo = 9;
        for (User student : students) {
            PortfolioReviewRequest request = PortfolioReviewRequest.builder()
                    .student(student)
                    .mentor(mentor)
                    .status(ReviewStatus.PENDING)
                    .createAt(LocalDateTime.now().minusDays(daysAgo))
                    .build();
            reviewRequestRepository.save(request);
            daysAgo = Math.max(0, daysAgo - 2);
        }

        log.info("DatabaseSeeder: Seeded {} pending review requests for the mentor.", students.size());
    }

    public void importMockUsersData(){
        log.info("DatabaseSeeder: Seeding Mock Data (Students, Counselors, Feedbacks, Progress)...");

        // ---------------------------- Import Admin Account ---------------------------- //
        User admin = userRepository.findByEmail("intelipath@gmail.com");
        if (admin == null) {
            admin = User.builder().email("intelipath@gmail.com").build();
        }
        admin.setFullName("intelipath");
        admin.setRole(UserRole.ADMIN);
        admin.setAccountType(AccountType.FPT);
        applySeedCredentials(admin, seedAccounts.getAdmin(), "admin");
        userRepository.save(admin);

        // -------------------------- Import Counselor Account -------------------------- //
        User userCou = userRepository.findByEmail(SEEDED_COUNSELOR_EMAIL);
        if (userCou == null) {
            userCou = userRepository.findByEmail(LEGACY_COUNSELOR_EMAIL);
        }
        if (userCou == null) {
            userCou = User.builder().build();
        }
        userCou.setEmail(SEEDED_COUNSELOR_EMAIL);
        userCou.setFullName("Nguyen Thao Vy");
        userCou.setRole(UserRole.COUNSELOR);
        // FPT so this counselor's student list resolves to the FPT students below.
        userCou.setAccountType(AccountType.FPT);
        applySeedCredentials(userCou, seedAccounts.getCounselor(), "counselor");
        userCou = userRepository.save(userCou);

        AcademicCounselor counselor = academicCounselorRepository.findByUserId(userCou.getUserId());
        if (counselor == null) {
            counselor = AcademicCounselor.builder()
                    .userId(userCou.getUserId())
                    .universityName(FPT_UNIVERSITY_NAME)
                    .department("Software Engineer")
                    .build();
        }
        counselor.setDepartment("Software Engineer");
        academicCounselorRepository.save(counselor);

        // --------------------------- Import Mentor Account ---------------------------- //
        User userMen = userRepository.findByEmail(SEEDED_MENTOR_EMAIL);
        if (userMen == null) {
            userMen = userRepository.findByEmail(LEGACY_MENTOR_EMAIL);
        }
        if (userMen == null) {
            userMen = User.builder().build();
        }
        userMen.setEmail(SEEDED_MENTOR_EMAIL);
        userMen.setFullName("Nguyen Minh Quan");
        userMen.setRole(UserRole.MENTOR);
        // OTHER because an industry mentor works at a company, not FPT. The value is inert
        // for this role — the FPT gate only guards STUDENT endpoints — so it costs nothing
        // to keep it honest rather than copying the counselor's FPT out of habit.
        userMen.setAccountType(AccountType.OTHER);
        applySeedCredentials(userMen, seedAccounts.getMentor(), "mentor");
        userMen = userRepository.save(userMen);

        IndustryMentor mentor = industryMentorRepository.findById(userMen.getUserId()).orElse(null);
        if (mentor == null) {
            mentor = IndustryMentor.builder().userId(userMen.getUserId()).build();
        }
        mentor.setCompany("FPT Software");
        mentor.setIndustryFocus("Software Engineer");
        industryMentorRepository.save(mentor);

        // -------------------------- Import Students Accounts -------------------------- //
        // ------------------ Specific Student Account ------------------ //
        User userSt = userRepository.findByEmail("dpvinh30092005@gmail.com");
        if (userSt == null) {
            userSt = User.builder().email("dpvinh30092005@gmail.com").build();
        }
        userSt.setFullName("Dang Phuoc Vinh");
        // A plausible undergraduate age: at 10 the profile form rightly rejects every
        // admission date, which made the seed account untestable.
        userSt.setYob(LocalDate.now().minusYears(20));
        userSt.setRole(UserRole.STUDENT);
        // Stands in for a counselor-provisioned FPT student: local credential + FPT material.
        userSt.setAccountType(AccountType.FPT);
        applySeedCredentials(userSt, seedAccounts.getStudent(), "student");
        userSt = userRepository.save(userSt);

        Student st = studentRepository.findByUserId(userSt.getUserId());
        if (st == null) {
            st = Student.builder().userId(userSt.getUserId()).build();
        }
        st.setUniversityName(FPT_UNIVERSITY_NAME);
        st.setCareerRole(careerRoleRepository.findByCareerName("Backend"));
        st.setMajor("Software Engineer");
        // Re-derived rather than filled-only: this account's identity is defined here,
        // and it has been renamed since it was first seeded, so a slug left over from
        // the old name would still be pointing at it.
        st.setPortfolioSlug(portfolioSlugService.allocateFor(userSt));
        studentRepository.save(st);

        seedSeniorDemoStudent();

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
                    // FPT on purpose, despite the plan once calling for OTHER here.
                    //
                    // These rows are fixtures, not accounts: they get no username and no
                    // password, and login goes through findByUsername, so nobody can ever
                    // sign in as one. That makes accountType irrelevant to FPT resource
                    // gating for them -- there is no session to gate -- and leaves it
                    // meaning exactly one thing: whether the FPT counselor can see them
                    // (StudentRepository.findStudentInfos scopes on accountType).
                    // Flipping these to OTHER would empty the counselor's list down to the
                    // single credentialed seed student.
                    .accountType(AccountType.FPT)
                    .build();
            sUser = userRepository.save(sUser);

            Student stu = Student.builder()
                    .userId(sUser.getUserId())
                    .portfolioSlug(portfolioSlugService.allocateFor(sUser))
                    .careerRole(frontend)
                    .universityName(FPT_UNIVERSITY_NAME)
                    .admissionDate(LocalDate.now().minusYears(random.nextInt(4)).withMonth(9).withDayOfMonth(1))
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

    /**
     * A reproducible end-state account for demos and visual QA.
     *
     * <p>This is deliberately separate from the ordinary credentialed student:
     * restarting a dev database may reset this fixture to its documented state,
     * but must never overwrite a developer's own progress. Every write is an
     * upsert, so application restarts do not multiply rows.
     */
    private void seedSeniorDemoStudent() {
        CareerRole backend = careerRoleRepository.findByCareerName("Backend");
        if (backend == null) {
            log.warn("DatabaseSeeder: Cannot seed senior demo student: Backend career not found.");
            return;
        }

        User demoUser = userRepository.findByEmail("senior.demo@intelipath.local");
        if (demoUser == null) {
            demoUser = User.builder().email("senior.demo@intelipath.local").build();
        }
        demoUser.setFullName("Senior Backend Demo");
        demoUser.setYob(LocalDate.now().minusYears(23));
        demoUser.setRole(UserRole.STUDENT);
        demoUser.setAccountType(AccountType.FPT);
        SeedAccountsProperties.Account demoCredentials = seedAccounts.getDemoStudent();
        if (!demoCredentials.isUsable() && seedAccounts.getStudent().isUsable()) {
            // Local convenience without committing a password: the demo gets its
            // own username and reuses the environment-supplied student password.
            demoCredentials = new SeedAccountsProperties.Account();
            demoCredentials.setUsername("senior_demo");
            demoCredentials.setPassword(seedAccounts.getStudent().getPassword());
        }
        applySeedCredentials(demoUser, demoCredentials, "demo student");
        demoUser = userRepository.save(demoUser);

        Student demo = studentRepository.findByUserId(demoUser.getUserId());
        if (demo == null) {
            demo = Student.builder().userId(demoUser.getUserId()).build();
        }
        demo.setUniversityName(FPT_UNIVERSITY_NAME);
        demo.setMajor("Software Engineer");
        demo.setCareerRole(backend);
        demo.setAdmissionDate(LocalDate.now().minusYears(4).withMonth(9).withDayOfMonth(1));
        demo.setPortfolioSlug(portfolioSlugService.allocateFor(demoUser));
        demo = studentRepository.save(demo);

        List<CareerRequiredSkill> core = careerRequiredSkillRepository
                .findByCareerRole_CareerIdAndImportanceLevelIn(
                        backend.getCareerId(), Set.of(ImportanceLevel.HIGH));
        LinkedHashMap<UUID, Skill> distinctCore = new LinkedHashMap<>();
        for (CareerRequiredSkill row : core) {
            if (row.getSkill() != null && row.getSkill().getSkillId() != null) {
                distinctCore.putIfAbsent(row.getSkill().getSkillId(), row.getSkill());
            }
        }

        int heldTarget = distinctCore.isEmpty()
                ? 0
                : Math.min(distinctCore.size() - 1,
                        Math.max(1, (int) Math.ceil(distinctCore.size() * 0.90)));
        Map<UUID, StudentSkill> existingSkills = studentSkillRepository
                .findByStudent_UserId(demo.getUserId()).stream()
                .filter(row -> row.getSkill() != null)
                .collect(java.util.stream.Collectors.toMap(
                        row -> row.getSkill().getSkillId(), row -> row, (left, right) -> left));
        List<StudentSkill> skillRows = new ArrayList<>();
        List<Map<String, Object>> questions = new ArrayList<>();
        List<Map<String, Object>> answers = new ArrayList<>();
        int index = 0;
        for (Skill skill : distinctCore.values()) {
            boolean held = index++ < heldTarget;
            StudentSkill studentSkill = existingSkills.getOrDefault(skill.getSkillId(),
                    StudentSkill.builder().student(demo).skill(skill).build());
            studentSkill.setProficiency((short) (held ? 4 : 2));
            studentSkill.setSelfDeclared(!held);
            studentSkill.setVerifiedBy(held ? "MENTOR" : null);
            skillRows.add(studentSkill);
            questions.add(Map.of(
                    "skillId", skill.getSkillId().toString(),
                    "skillName", skill.getSkillName(),
                    "prompt", "Rate your practical proficiency in " + skill.getSkillName()));
            answers.add(Map.of(
                    "skillId", skill.getSkillId().toString(),
                    "skillName", skill.getSkillName(),
                    "level", held ? 4 : 2,
                    "note", "Completed demo assessment answer"));
        }
        studentSkillRepository.saveAll(skillRows);

        BigDecimal ratio = distinctCore.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf((double) heldTarget / distinctCore.size()).setScale(2, java.math.RoundingMode.HALF_UP);
        StudentAssessment assessment = studentAssessmentRepository.findByUserIdOrderByCreatedAtDesc(demo.getUserId())
                .stream()
                .filter(row -> "DEMO_SEED".equals(row.getModelUsed()))
                .findFirst()
                .orElseGet(StudentAssessment::new);
        assessment.setUserId(demo.getUserId());
        assessment.setCareerId(backend.getCareerId());
        assessment.setQuestions(questions);
        assessment.setAnswers(answers);
        assessment.setAiLevel(SeniorityLevel.SENIOR);
        assessment.setAiRawLevel(SeniorityLevel.SENIOR);
        assessment.setAiRationale("Seeded demo: answered every core-skill question; 90% objectively verified at professional proficiency.");
        assessment.setAiConfidence(BigDecimal.ONE);
        assessment.setRatioAll(ratio);
        assessment.setRatioVerified(ratio);
        assessment.setRequiredCount(distinctCore.size());
        assessment.setModelUsed("DEMO_SEED");
        assessment.setStatus("COMPLETED");
        assessment.setComputedAt(LocalDateTime.now());

        List<SkillNode> publishedNodes = skillNodeRepository
                .findPublishedForCareerLegacyOrder(backend.getCareerId());
        assessment.setAppliedNodeCount(publishedNodes.size());
        studentAssessmentRepository.save(assessment);

        Map<UUID, StudentProgress> existingProgress = studentProgressRepository
                .findByStudent_UserId(demo.getUserId()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> row.getSkillNode().getNodeId(), row -> row, (left, right) -> left));
        LocalDateTime completedAt = LocalDateTime.now().minusDays(1);
        List<StudentProgress> progressRows = new ArrayList<>(publishedNodes.size());
        for (SkillNode node : publishedNodes) {
            StudentProgress progress = existingProgress.getOrDefault(node.getNodeId(),
                    StudentProgress.builder().student(demo).skillNode(node).createdAt(completedAt).build());
            progress.setStatus(RoadmapStepStatus.COMPLETED);
            progress.setCompletedAt(completedAt);
            progressRows.add(progress);
        }
        studentProgressRepository.saveAll(progressRows);
        log.info("DatabaseSeeder: Senior demo student ready: {} assessment answers, {} completed roadmap nodes.",
                answers.size(), progressRows.size());
    }
}
