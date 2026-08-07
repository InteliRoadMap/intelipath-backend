package com.inteliroadmap.backend.components;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inteliroadmap.backend.domain.dto.response.market.SkillDemandResponse;
import com.inteliroadmap.backend.domain.dto.response.plan.LearningPlanResponse;
import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.NodeType;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
import com.inteliroadmap.backend.domain.enums.SeniorityLevel;
import com.inteliroadmap.backend.domain.enums.StageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalizedPlanBuilderTest {

    private PersonalizedPlanBuilder builder;
    private final ObjectMapper mapper = new ObjectMapper();

    private Skill javascript;
    private Skill graphql;

    private final Map<UUID, String> statusByNodeId = new HashMap<>();

    @BeforeEach
    void setUp() {
        builder = new PersonalizedPlanBuilder();
        statusByNodeId.clear();
        javascript = skill("JavaScript");
        graphql = skill("GraphQL");
    }

    /**
     * The defect this ordering exists to fix: the plan told a FRESHER to learn
     * GraphQL before JavaScript. Stage must beat every score, so the test stacks
     * the deck against JavaScript on every other axis.
     */
    @Test
    void foundationBeatsAdvancedEvenWhenAdvancedWinsOnEveryScore() {
        List<SkillNode> nodes = List.of(
                node("JavaScript Basics", javascript, StageType.FOUNDATION),
                node("GraphQL Federation", graphql, StageType.ADVANCED));

        LearningPlanResponse plan = builder.build(
                "Frontend",
                SeniorityLevel.FRESHER,
                List.of(required(javascript, ImportanceLevel.AVG), required(graphql, ImportanceLevel.HIGH)),
                List.of(),
                nodes,
                Map.of(graphql.getSkillId(), demand(0.95), javascript.getSkillId(), demand(0.01)),
                statusByNodeId);

        assertEquals(List.of("JavaScript", "GraphQL"),
                plan.getSteps().stream().map(s -> s.getSkillName()).toList(),
                "a foundation skill must never be ordered after an advanced one");
        assertEquals(1, plan.getSteps().get(0).getOrder());
    }

    /**
     * Depth is what separates a backbone skill from a specialism. On the real
     * data JavaScript owns a top-level topic while React only ever appears as a
     * child inside one — and stage alone cannot see that, because both have
     * FOUNDATION nodes somewhere.
     */
    @Test
    void aSkillThatOwnsATopLevelTopicComesBeforeOneBuriedInsideAnother() {
        Skill react = skill("React");
        SkillNode javascriptTopic = node("JavaScript", javascript, StageType.FOUNDATION);
        SkillNode reactChild = node("React", react, StageType.FOUNDATION);
        reactChild.setParentNode(javascriptTopic);

        LearningPlanResponse plan = builder.build(
                "Frontend", SeniorityLevel.BEGINNER,
                List.of(required(javascript, ImportanceLevel.AVG), required(react, ImportanceLevel.HIGH)),
                List.of(), List.of(javascriptTopic, reactChild),
                Map.of(react.getSkillId(), demand(0.95)), statusByNodeId);

        assertEquals(List.of("JavaScript", "React"),
                plan.getSteps().stream().map(s -> s.getSkillName()).toList());
    }

    /**
     * The catalog and the roadmap spell things differently. Measured on the real
     * Frontend data, 43 of 115 core skills had no node by id — including thirteen
     * HIGH ones (CSS3, HTML5, TypeScript, "Git and Version Control"), all of them
     * taught under a slightly different name. Matching by id alone dropped them
     * from the plan without a word.
     */
    @Test
    void aSkillFindsItsMaterialEvenWhenTheNodeSpellsItDifferently() {
        Skill css3 = skill("CSS3");
        Skill gitAndVcs = skill("Git and Version Control");
        // The nodes carry the roadmap's spelling and a DIFFERENT skill id.
        SkillNode cssNode = node("CSS", skill("CSS"), StageType.FOUNDATION);
        SkillNode vcsNode = node("Version Control", skill("Version Control"), StageType.FOUNDATION);

        LearningPlanResponse plan = builder.build(
                "Frontend", SeniorityLevel.BEGINNER,
                List.of(required(css3, ImportanceLevel.HIGH), required(gitAndVcs, ImportanceLevel.HIGH)),
                List.of(), List.of(cssNode, vcsNode), Map.of(), statusByNodeId);

        assertEquals(2, plan.getSteps().size(),
                "both skills are taught, just under another name: " + plan.getSteps());
        assertTrue(plan.getSteps().stream().anyMatch(st -> "CSS3".equals(st.getSkillName())));
        assertTrue(plan.getSteps().stream().anyMatch(st -> "Git and Version Control".equals(st.getSkillName())));
    }

    /** Name matching must not be so loose that unrelated skills borrow each other's material. */
    @Test
    void nameMatchingDoesNotInventMaterialForAnUnrelatedSkill() {
        Skill kubernetes = skill("Kubernetes");
        SkillNode cssNode = node("CSS", skill("CSS"), StageType.FOUNDATION);

        LearningPlanResponse plan = builder.build(
                "Frontend", SeniorityLevel.BEGINNER,
                List.of(required(kubernetes, ImportanceLevel.HIGH)),
                List.of(), List.of(cssNode), Map.of(), statusByNodeId);

        assertTrue(plan.getSteps().isEmpty());
    }

    /** Within one stage the score decides, otherwise the ranking does nothing. */
    @Test
    void withinAStageTheScoreStillDecides() {
        Skill css = skill("CSS");
        List<SkillNode> nodes = List.of(
                node("JavaScript Basics", javascript, StageType.FOUNDATION),
                node("CSS Basics", css, StageType.FOUNDATION));

        LearningPlanResponse plan = builder.build(
                "Frontend", SeniorityLevel.FRESHER,
                List.of(required(javascript, ImportanceLevel.LOW), required(css, ImportanceLevel.HIGH)),
                List.of(), nodes, Map.of(), statusByNodeId);

        assertEquals("CSS", plan.getSteps().get(0).getSkillName());
    }

    /** Proposing work the roadmap would refuse to unlock contradicts the product. */
    @Test
    void skillsWhoseMaterialIsAllLockedAreLeftOut() {
        SkillNode locked = node("GraphQL Federation", graphql, StageType.ADVANCED);
        statusByNodeId.put(locked.getNodeId(), "locked");

        LearningPlanResponse plan = builder.build(
                "Frontend", SeniorityLevel.FRESHER,
                List.of(required(graphql, ImportanceLevel.HIGH)),
                List.of(), List.of(locked), Map.of(), statusByNodeId);

        assertTrue(plan.getSteps().isEmpty());
    }

    /** A skill the student can already evidence is skipped, with its reason attached. */
    @Test
    void coveredSkillsAreSkippedAndExplained() {
        StudentSkill held = StudentSkill.builder()
                .student(Student.builder().userId(UUID.randomUUID()).build())
                .skill(javascript)
                .proficiency((short) 4)
                .verifiedBy("GITHUB")
                .build();

        LearningPlanResponse plan = builder.build(
                "Frontend", SeniorityLevel.JUNIOR,
                List.of(required(javascript, ImportanceLevel.HIGH)),
                List.of(held),
                List.of(node("JavaScript Basics", javascript, StageType.FOUNDATION)),
                Map.of(), statusByNodeId);

        assertTrue(plan.getSteps().isEmpty());
        assertEquals(1, plan.getAlreadyCovered().size());
        assertEquals("GITHUB", plan.getAlreadyCovered().get(0).getVerifiedBy());
        assertEquals(1, plan.getCoveredSkillCount());
    }

    /** The justification must name real evidence, never invent market numbers. */
    @Test
    void reasonQuotesMarketFiguresOnlyWhenTheyExist() {
        List<SkillNode> nodes = List.of(node("JavaScript Basics", javascript, StageType.FOUNDATION));

        LearningPlanResponse withData = builder.build(
                "Frontend", SeniorityLevel.FRESHER,
                List.of(required(javascript, ImportanceLevel.HIGH)), List.of(), nodes,
                Map.of(javascript.getSkillId(), demandWithCounts(34, 120)), statusByNodeId);
        assertTrue(withData.getSteps().get(0).getWhy().contains("34 of 120"));

        LearningPlanResponse withoutData = builder.build(
                "Frontend", SeniorityLevel.FRESHER,
                List.of(required(javascript, ImportanceLevel.HIGH)), List.of(), nodes,
                Map.of(), statusByNodeId);
        String why = withoutData.getSteps().get(0).getWhy();
        assertFalse(why.contains("postings"), "no market data means no market claim: " + why);
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private Skill skill(String name) {
        Skill skill = new Skill();
        skill.setSkillId(UUID.randomUUID());
        skill.setSkillName(name);
        return skill;
    }

    private CareerRequiredSkill required(Skill skill, ImportanceLevel importance) {
        return CareerRequiredSkill.builder().skill(skill).importanceLevel(importance).build();
    }

    private SkillNode node(String name, Skill skill, StageType stage) {
        List<String> links = new ArrayList<>(List.of("https://example.test/a", "https://example.test/b"));
        return SkillNode.builder()
                .nodeId(UUID.randomUUID())
                .nodeName(name)
                .nodeLevel(0)
                .skill(skill)
                .type(NodeType.builder().stage(stage).build())
                .resource(mapper.valueToTree(links))
                .build();
    }

    private SkillDemandResponse demand(double frequency) {
        return SkillDemandResponse.builder().frequency(frequency).build();
    }

    private SkillDemandResponse demandWithCounts(int jobCount, int sampleSize) {
        return SkillDemandResponse.builder()
                .frequency((double) jobCount / sampleSize)
                .jobCount(jobCount)
                .sampleSize(sampleSize)
                .build();
    }
}
