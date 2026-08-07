package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.dto.response.market.SkillDemandResponse;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StackBranchScorerTest {

    private StackBranchScorer scorer;

    private Skill java;
    private Skill spring;
    private Skill csharp;
    private SkillNode javaBranch;
    private SkillNode csharpBranch;

    private Map<UUID, List<SkillNode>> childrenByParent;
    private Map<UUID, StudentSkill> held;
    private Map<UUID, SkillDemandResponse> demand;

    @BeforeEach
    void setUp() {
        scorer = new StackBranchScorer();

        java = skill("Java");
        spring = skill("Spring Boot");
        csharp = skill("C#");

        javaBranch = node("Java", null, java);
        csharpBranch = node("C#", null, csharp);

        childrenByParent = new HashMap<>();
        held = new HashMap<>();
        demand = new HashMap<>();

        demand.put(java.getSkillId(), relevance(0.30));
        demand.put(spring.getSkillId(), relevance(0.20));
        demand.put(csharp.getSkillId(), relevance(0.30));
    }

    @Test
    void picksTheBranchTheStudentActuallyHasSkillsIn() {
        held.put(java.getSkillId(), studentSkill(java, 3, null));

        StackBranchScorer.BranchVerdict verdict =
                scorer.pick(List.of(javaBranch, csharpBranch), childrenByParent, held, demand);

        assertNotNull(verdict);
        assertEquals("Java", verdict.chosen().getNodeName());
    }

    /**
     * The case name matching gets wrong on the real catalog: the branch worth
     * picking is the one whose subtree teaches what the student knows, not the
     * one whose own label happens to match.
     */
    @Test
    void scoresTheWholeSubtreeNotJustTheBranchNode() {
        // C# is the stronger match on the branch node itself; Java wins on depth.
        held.put(csharp.getSkillId(), studentSkill(csharp, 2, null));
        held.put(spring.getSkillId(), studentSkill(spring, 4, "GITHUB"));

        SkillNode springChild = node("Spring Boot", javaBranch, spring);
        childrenByParent.put(javaBranch.getNodeId(), List.of(springChild));

        StackBranchScorer.BranchVerdict verdict =
                scorer.pick(List.of(javaBranch, csharpBranch), childrenByParent, held, demand);

        assertNotNull(verdict);
        assertEquals("Java", verdict.chosen().getNodeName());
        assertTrue(verdict.reason().contains("Spring Boot"), verdict.reason());
    }

    @Test
    void verifiedEvidenceOutweighsAnEqualSelfReport() {
        // Same skill strength on both sides; only the evidence differs.
        held.put(java.getSkillId(), studentSkill(java, 3, "GITHUB"));
        held.put(csharp.getSkillId(), studentSkill(csharp, 3, null));

        StackBranchScorer.BranchVerdict verdict =
                scorer.pick(List.of(javaBranch, csharpBranch), childrenByParent, held, demand);

        assertNotNull(verdict);
        assertEquals("Java", verdict.chosen().getNodeName());
        assertTrue(verdict.reason().contains("(verified)"), verdict.reason());
    }

    /** A decision the student has equal grounds for is theirs, not ours. */
    @Test
    void tiesAreLeftToTheStudent() {
        held.put(java.getSkillId(), studentSkill(java, 3, null));
        held.put(csharp.getSkillId(), studentSkill(csharp, 3, null));

        assertNull(scorer.pick(List.of(javaBranch, csharpBranch), childrenByParent, held, demand));
    }

    @Test
    void aStudentWithNothingInEitherBranchGetsNoPick() {
        assertNull(scorer.pick(List.of(javaBranch, csharpBranch), childrenByParent, held, demand));
    }

    /**
     * Market relevance is required, not optional: without it the score is a
     * popularity contest between whatever the student happened to type.
     */
    @Test
    void skillsWithNoMarketRelevanceDoNotScore() {
        held.put(java.getSkillId(), studentSkill(java, 4, "GITHUB"));

        assertNull(scorer.pick(List.of(javaBranch, csharpBranch), childrenByParent, held, Map.of()));
    }

    /** A single alternative is not a choice; nothing to decide. */
    @Test
    void aGroupWithOneAlternativeIsNotDecided() {
        held.put(java.getSkillId(), studentSkill(java, 4, "GITHUB"));

        assertNull(scorer.pick(List.of(javaBranch), childrenByParent, held, demand));
    }

    /** A parent/child cycle in the data must not hang the request. */
    @Test
    void aCycleInTheTreeTerminates() {
        held.put(java.getSkillId(), studentSkill(java, 4, "GITHUB"));
        SkillNode child = node("Spring Boot", javaBranch, spring);
        held.put(spring.getSkillId(), studentSkill(spring, 3, null));
        childrenByParent.put(javaBranch.getNodeId(), List.of(child));
        childrenByParent.put(child.getNodeId(), List.of(javaBranch));   // back edge

        StackBranchScorer.BranchVerdict verdict =
                scorer.pick(List.of(javaBranch, csharpBranch), childrenByParent, held, demand);

        assertNotNull(verdict);
        assertEquals("Java", verdict.chosen().getNodeName());
    }

    /**
     * A branch that names one skill on nine of its children must not beat a
     * deeper branch on repetition.
     */
    @Test
    void aSkillRepeatedAcrossChildrenIsCountedOnce() {
        held.put(java.getSkillId(), studentSkill(java, 3, null));
        childrenByParent.put(javaBranch.getNodeId(), List.of(
                node("Java again", javaBranch, java),
                node("Java once more", javaBranch, java)));

        StackBranchScorer.BranchVerdict verdict =
                scorer.pick(List.of(javaBranch, csharpBranch), childrenByParent, held, demand);

        assertNotNull(verdict);
        // 3 × 0.30 × 1 counted once, not three times.
        assertEquals(0.9, verdict.score(), 0.0001);
    }

    // ── rank(): the ranking pick() used to compute and throw away ────────────

    @Test
    void rankReturnsEveryBranch_notOnlyTheWinner() {
        held.put(java.getSkillId(), studentSkill(java, 3, null));

        StackBranchScorer.Ranking ranking =
                scorer.rank(List.of(javaBranch, csharpBranch), childrenByParent, held, demand);

        assertEquals(2, ranking.ranked().size());
        assertEquals("Java", ranking.ranked().get(0).node().getNodeName());
        assertEquals("C#", ranking.ranked().get(1).node().getNodeName());
        assertEquals(StackBranchScorer.Verdict.DECISIVE, ranking.verdict());
    }

    /**
     * The honest states are the point of exposing the ranking at all: a UI that
     * highlights ranked[0] regardless would present a coin toss as advice.
     */
    @Test
    void twoBranchesWithinTheMarginAreTooCloseToCall() {
        // 3 × 0.30 vs 3 × 0.29 — inside DECISIVE_MARGIN (10%).
        demand.put(csharp.getSkillId(), relevance(0.29));
        held.put(java.getSkillId(), studentSkill(java, 3, null));
        held.put(csharp.getSkillId(), studentSkill(csharp, 3, null));

        StackBranchScorer.Ranking ranking =
                scorer.rank(List.of(javaBranch, csharpBranch), childrenByParent, held, demand);

        assertEquals(StackBranchScorer.Verdict.TOO_CLOSE, ranking.verdict());
        assertEquals(2, ranking.ranked().size(), "a tie still ranks; it just names no winner");
    }

    @Test
    void aStudentWithNothingInAnyBranchGetsNoSignal() {
        StackBranchScorer.Ranking ranking =
                scorer.rank(List.of(javaBranch, csharpBranch), childrenByParent, held, demand);

        assertEquals(StackBranchScorer.Verdict.NO_SIGNAL, ranking.verdict());
        assertEquals(0.0, ranking.ranked().get(0).score(), 0.0001);
    }

    /**
     * The reason both exist: what the student is shown and what the system would
     * have done must come from one pass, or the roadmap can recommend Java and
     * silently select C#.
     */
    @Test
    void pickAndRankNeverDisagreeAboutTheWinner() {
        held.put(java.getSkillId(), studentSkill(java, 4, "GITHUB"));
        held.put(csharp.getSkillId(), studentSkill(csharp, 1, null));

        StackBranchScorer.BranchVerdict verdict =
                scorer.pick(List.of(javaBranch, csharpBranch), childrenByParent, held, demand);
        StackBranchScorer.Ranking ranking =
                scorer.rank(List.of(javaBranch, csharpBranch), childrenByParent, held, demand);

        assertNotNull(verdict);
        assertEquals(StackBranchScorer.Verdict.DECISIVE, ranking.verdict());
        assertEquals(verdict.chosen().getNodeId(), ranking.ranked().get(0).node().getNodeId());
        assertEquals(verdict.score(), ranking.ranked().get(0).score(), 0.0001);
    }

    @Test
    void pickStaysSilentWheneverRankIsNotDecisive() {
        demand.put(csharp.getSkillId(), relevance(0.29));
        held.put(java.getSkillId(), studentSkill(java, 3, null));
        held.put(csharp.getSkillId(), studentSkill(csharp, 3, null));

        assertNull(scorer.pick(List.of(javaBranch, csharpBranch), childrenByParent, held, demand));
    }

    private SkillDemandResponse relevance(double value) {
        return SkillDemandResponse.builder().relevance(value).build();
    }

    private StudentSkill studentSkill(Skill skill, int proficiency, String verifiedBy) {
        StudentSkill studentSkill = new StudentSkill();
        studentSkill.setSkill(skill);
        studentSkill.setProficiency((short) proficiency);
        studentSkill.setVerifiedBy(verifiedBy);
        return studentSkill;
    }

    private Skill skill(String name) {
        Skill skill = new Skill();
        skill.setSkillId(UUID.randomUUID());
        skill.setSkillName(name);
        return skill;
    }

    private SkillNode node(String name, SkillNode parent, Skill skill) {
        return SkillNode.builder()
                .nodeId(UUID.randomUUID())
                .nodeName(name)
                .parentNode(parent)
                .skill(skill)
                .build();
    }
}
