package com.inteliroadmap.backend.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

import java.util.UUID;

@Entity
@Table(name = "skill_nodes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SkillNode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "node_id")
    private UUID nodeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sn_career"))
    private CareerRole careerRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", foreignKey = @ForeignKey(name = "fk_sn_skill"))
    private Skill skill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id", foreignKey = @ForeignKey(name = "fk_sn_type"))
    private NodeType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_node", foreignKey = @ForeignKey(name = "fk_sn_previous_node"))
    private SkillNode previousNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_node", foreignKey = @ForeignKey(name = "fk_sn_parent_node"))
    private SkillNode parentNode;

    @Column(name = "node_name", nullable = false)
    private String nodeName;

    /** TOPIC | SKILL | CAPABILITY | CHECKPOINT. Never inferred by a client. */
    @Column(name = "semantic_type", nullable = false, length = 20)
    @Builder.Default
    private String semanticType = "CAPABILITY";

    /**
     * @deprecated the ordering index <em>inside</em> the source roadmap, not a
     * position on the career path and not a depth. It reads 0 on 2.610 nodes and
     * 1..14 on the rest, at every depth, so any rule built on it is a coincidence:
     * "root with node_level 0" once selected the imported roadmaps by accident and
     * missed Java entirely. Use {@link #depth} and {@link #sortOrder}. Kept
     * because the seeder still writes it.
     */
    @Deprecated
    @Column(name = "node_level")
    private Integer nodeLevel;

    /** Edges from the root: 0 is a top-level node. Derived from {@code parent_node}. */
    @Column(name = "depth")
    private Short depth;

    /**
     * Position among siblings — total and stable.
     *
     * <p>{@code node_level} left 585 sibling groups tied, so sibling order was
     * decided by whatever the database returned that day.
     */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /**
     * Descendants, excluding this node.
     *
     * <p>What separates a step from a roadmap: Java carries 71, Python 122. Stored
     * rather than counted per request because every consumer needs it and they
     * must all agree on the answer.
     */
    @Column(name = "subtree_size")
    private Integer subtreeSize;

    /** The depth-0 node this belongs to; equals {@code nodeId} for a root. */
    @Column(name = "root_node_id")
    private UUID rootNodeId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "resource", columnDefinition = "jsonb")
    private JsonNode resource;

    @Column(name = "completion_policy", length = 50)
    private String completionPolicy;

    // How a parent's children relate: ALL = learn them all; CHOOSE_ONE = pick one
    // alternative (e.g. one Backend language). Drives per-student selection.
    @Column(name = "selection", length = 20)
    private String selection;

    // For CHOOSE_ONE groups, how many children the student must pick (usually 1).
    @Column(name = "choose_count")
    private Integer chooseCount;

    // Visual/semantic role of the node: CORE | ALTERNATIVE | OPTIONAL.
    @Column(name = "node_kind", length = 20)
    private String nodeKind;

    /**
     * Which student level this node is for: 1 Beginner, 2 Intermediate, 3
     * Advanced. NULL means "show at every level" and is the safe default — a
     * node nobody has graded must not vanish from anyone's roadmap.
     *
     * <p>Derived from {@code depth} by {@code 2026-08-06_node_tier.sql}, not
     * from {@code difficulty}: difficulty is NULL on every row in the catalog.
     */
    @Column(name = "tier")
    private Short tier;

    // Layout axis: MAIN (on the roadmap spine) | BRANCH (rendered off to the side).
    @Column(name = "axis", length = 20)
    private String axis;

    // Dashed "nice to have" node that never blocks progress.
    @Column(name = "is_optional")
    private Boolean isOptional;

    // Milestone gate that visually marks the end of a stage.
    @Column(name = "is_checkpoint")
    private Boolean isCheckpoint;

    /**
     * The confidence bar evidence must clear to complete this node, <b>0-100</b>.
     *
     * <p>Read as a percentage by
     * {@code RoadmapPersonalizationServiceImpl.meetsNodeProficiency}, which divides
     * by 100 and takes the stricter of it and the skill's importance floor (HIGH
     * .85, AVG .70, LOW .60).
     *
     * <p><b>Two units once shared this column.</b> 3.706 rows held 1..4 — a
     * {@link com.inteliroadmap.backend.domain.enums.ProficiencyLevel}, not a
     * percentage — while 246 held 65. A 2 divided by 100 is 0.02, a bar nothing
     * can fail, so those nodes silently had no bar of their own at all. Converted
     * to the percentages the confidence table already uses (AWARE 40, PRACTICED
     * 55, APPLIED 70, PROFESSIONAL 85) and pinned by
     * {@code ck_skill_nodes_required_proficiency}, which rejects anything between
     * 1 and 9: a confidence bar below 10% is not a bar, it is a unit mistake.
     */
    @Column(name = "required_proficiency")
    private Integer requiredProficiency;

    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "evidence_keywords", columnDefinition = "jsonb")
    private JsonNode evidenceKeywords;

    /**
     * What must be learned before this node, and on whose authority.
     *
     * <p>{@code [{nodeId, source: SOURCE|AI|MENTOR, confidence, reason}]}. The
     * column existed from the start and was empty in all 4.177 rows, so every
     * ordering decision had to be guessed from node_level, then stage, then tree
     * depth. Populated from the roadmap authors' own {@code previous_id} first —
     * evidence rather than inference — which is why {@code source} travels with
     * each entry: a reader must be able to tell an author's decision from a
     * model's guess.
     */
    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "prerequisite", columnDefinition = "jsonb")
    private JsonNode prerequisite;

    /**
     * Relative effort, 1..4 — the same four bands as {@link
     * com.inteliroadmap.backend.domain.enums.ProficiencyLevel}.
     *
     * <p>Deliberately not {@code node_level}, which is a position in a sequence:
     * the twentieth node on the spine is not harder than the second, it is only
     * later. Difficulty is what lets a plan avoid stacking four demanding nodes
     * into one week.
     *
     * <p><b>Why 1..4 and not 1..5.</b> The column was declared 1..5 and is empty
     * on all 4.177 rows, so nothing had to be migrated to narrow it. Four is what
     * three independent sources use (MyEngineeringPath, Prosumely, FindSkill),
     * and — more usefully here — it is the scale the student is already read on:
     * a node at difficulty 3 is one that wants APPLIED. A fifth band would have
     * no counterpart on the proficiency side, so no consumer could say what it
     * meant.
     */
    @Column(name = "difficulty")
    private Integer difficulty;

    /**
     * Hours of focused study, assuming the prerequisites are already held.
     *
     * @deprecated no serious source estimates study hours <em>per node</em>.
     * MyEngineeringPath estimates whole paths against a starting point (18–24 /
     * 9–12 / 3–6 months); Prosumely uses 30-day / 3-month / 1-year markers. A
     * per-node hour figure is a number the student can measure and find wrong,
     * and being caught inventing one costs more than the field was ever worth.
     * Empty on all 4.177 rows and it stays that way — nothing writes it, nothing
     * should read it. Use {@link #difficulty} for "how hard", and the roadmap's
     * own size for "how long". Kept only so an existing database and any client
     * still reading the field keep working.
     */
    @Deprecated
    @Column(name = "estimated_hours")
    private Integer estimatedHours;

    /**
     * What the student can do once this node is finished — a JSON array of
     * strings, phrased as capabilities rather than topics ("containerise a Spring
     * Boot app", not "Docker basics"). A node whose objectives cannot be written
     * concretely is usually a heading that should not be a node at all.
     */
    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "objectives", columnDefinition = "jsonb")
    private JsonNode objectives;

    /** Why this sits on the path to the career, in a sentence or two. */
    @Column(name = "why_it_matters", columnDefinition = "TEXT")
    private String whyItMatters;

    /**
     * Publication state, FR2.3: {@code PUBLISHED} or {@code DRAFT}.
     *
     * <p>A leaf carrying fewer than two resource links cannot teach anything, so it
     * is withheld from students — 285 nodes today. Headers and checkpoints are
     * exempt: the material for a header lives in its children, and a checkpoint is
     * something the student builds rather than reads.
     *
     * <p><b>Read-only on purpose.</b> The column is set by the seeder and by the
     * gate in the init SQL, never by application code, so it is mapped
     * {@code insertable = false, updatable = false}: a new row takes the database
     * default {@code 'PUBLISHED'} and no service can quietly unpublish a node as a
     * side effect of saving something else. Curation is a deliberate act, and it
     * happens where the resource links are.
     */
    @Column(name = "status", insertable = false, updatable = false)
    private String status;
}

