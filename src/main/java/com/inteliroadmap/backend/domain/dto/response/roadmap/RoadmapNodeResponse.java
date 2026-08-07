package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadmapNodeResponse {
    private UUID nodeId;
    private String nodeName;
    /** TOPIC | SKILL | CAPABILITY | CHECKPOINT. */
    private String semanticType;
    /** Depth relative to the currently opened roadmap root. */
    private Integer relativeDepth;
    private RoadmapTopicResponse topic;
    private RoadmapSkillResponse skill;
    private String parentNode;
    private String previousNode;
    private Integer nodeLevel;
    private String stage;
    private String completionPolicy;
    private Integer weight;
    private Integer requiredProficiency;
    // Topic (spine) node that owns child sub-skills and auto-completes from them.
    private Boolean parentTopic;

    /** Distance from a root: 0 = spine, 1 = its direct children, and so on. */
    private Integer depth;

    /**
     * Children the visibility filter held back — because they sit deeper than the
     * default payload carries, or well above the student's level.
     *
     * <p>Sent so the card can offer them ("+12 sâu hơn") instead of pretending the
     * topic is a leaf. Zero or null means everything below this node is already here.
     */
    private Integer hiddenChildren;
    private Integer childTotal;
    private Integer childCompleted;
    // Roadmap selection semantics (v2). selection: ALL | CHOOSE_ONE; nodeKind:
    // CORE | ALTERNATIVE | OPTIONAL; axis: MAIN (spine) | BRANCH. Drive how the
    // frontend renders choice groups, alternatives, and optional/checkpoint nodes.
    private String selection;
    private Integer chooseCount;
    private String nodeKind;

    /** 1 Beginner · 2 Intermediate · 3 Advanced. Null means "shown at any level". */
    private Short tier;

    /**
     * True when {@link #tier} is above what the student's level unlocks.
     *
     * <p>Locked, not withheld: a student who cannot yet see the road continues
     * cannot tell a roadmap that ends here from one that has more waiting.
     */
    private Boolean tierLocked;
    private String axis;
    private Boolean isOptional;
    private Boolean isCheckpoint;
    // Layout metadata (presentation only, sourced from roadmap_node_layouts).
    private Double positionX;
    private Double positionY;
    private String lane;
    private Integer displayOrder;
    private Integer layoutVersion;
    private String description;
    private Object resource;
    // Node depth: what the card shows once a student opens it. Null until the
    // enrichment pass has run, so the card must degrade to name + description
    // rather than render an empty section.
    /**
     * What the student can already prove on this node's skill: 1 AWARE,
     * 2 PRACTICED, 3 APPLIED, 4 PROFESSIONAL. Null when nothing is claimed.
     *
     * <p>The node carried {@code requiredProficiency} — the bar — with no way to
     * see where the student stands against it, which makes the bar unreadable:
     * "needs APPLIED" answers nothing until you know you are at PRACTICED.
     */
    private Short currentProficiency;

    /** TRANSCRIPT | GITHUB | MENTOR, or null when the student declared it themselves. */
    private String proficiencyVerifiedBy;

    /**
     * The rule that finishes this node, in a sentence the student can check
     * against what they see: "Finishes at 60% of its sub-skills (3 of 8 done)".
     *
     * <p>Auto-completion was invisible before — a topic ticked itself and nothing
     * on screen had said it would, or what was still missing.
     */
    private String completionRule;

    /** Evidence rows whose skill/node identity can directly support this node. */
    private java.util.List<RoadmapNodeEvidenceResponse> evidence;
    /** Effective confidence bar after node proficiency and career importance are combined. */
    private Double evidenceRequiredConfidence;
    /** Plain-language explanation of why evidence did or did not complete the node. */
    private String evidenceDecision;

    /** Descendants inside this node. Large ones are roadmaps, not steps. */
    private Integer subtreeSize;

    /**
     * True when clicking opens this node as its own roadmap rather than
     * expanding it in place — Java's 71 nodes belong on their own canvas.
     */
    private Boolean entersRoadmap;

    /** Relative effort, 1..4 — the same bands as proficiency. */
    private Integer difficulty;

    /**
     * @deprecated always null. No source estimates study hours per node, so this
     * is never written and must not be rendered — see
     * {@link com.inteliroadmap.backend.domain.entity.SkillNode#getEstimatedHours()}.
     * Still on the wire so a client that reads it does not break; no client does.
     */
    @Deprecated
    private Integer estimatedHours;
    private Object objectives;
    private String whyItMatters;
    private String status;
    // ISO-8601 timestamp of when the student completed this node (null if not completed).
    private String completedAt;
    // FLM (FPT curriculum) overlay: which FPT subjects teach this node's skill, and the
    // concrete lesson resources for it. Null/empty when the node maps to no catalog skill.
    private FptNodeCoverageResponse fptCoverage;
    private java.util.List<FptNodeResourceResponse> fptResources;

    // ── Catalog skill + market demand (additive; all nullable) ───────────────
    // The node's linked catalog skill and how much the job market currently asks
    // for it. Every field here is optional on the wire: a client that does not
    // know about them renders exactly as before, and a node with no linked skill
    // or no postings behind it simply carries nulls rather than zeroes.
    /** Display name of the linked catalog skill, e.g. "Spring Boot". */
    private String skillName;
    /** LANGUAGE | FRAMEWORK | LIBRARY | DATABASE | PLATFORM | TOOL | TESTING | PROTOCOL | CONCEPT. */
    private String skillCategory;
    /**
     * Demand for {@link #skillName} in recent postings. Null when the node maps to
     * no skill, or when too few postings mention it to say anything honest.
     */
    private com.inteliroadmap.backend.domain.dto.response.market.SkillDemandResponse marketDemand;

    // ── Learning priority (additive; all nullable) ───────────────────────────
    // The score RoadmapEdgeResolver already ordered this node by, now visible.
    // It is the same number the ordering runs on, not a second opinion, so the
    // badge can never disagree with the position it sits in.
    /** 0..1 — importance 0.4, market relevance 0.3, level fit 0.3. */
    private Double priorityScore;
    /** CRITICAL at 0.75, HIGH at 0.55, otherwise NORMAL. */
    private String priorityLabel;
    /**
     * Why it scored that, one clause per term of the formula, e.g. "essential for
     * this career, 19% of recent postings ask for it, you do not have it yet".
     * A term with no signal behind it contributes no clause.
     */
    private String priorityReason;
}
