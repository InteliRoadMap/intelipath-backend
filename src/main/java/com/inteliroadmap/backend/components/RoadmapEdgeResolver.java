package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.dto.response.market.SkillDemandResponse;
import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
import com.inteliroadmap.backend.domain.enums.SeniorityLevel;
import com.inteliroadmap.backend.domain.enums.StageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Computes the order of a roadmap from the student's profile, replacing the
 * static {@code previous_node} column as the source of "learn this before that".
 *
 * <p>Why this exists: measured on the real 1.200-node table, every node at level
 * ≥1 chained to the node one level below it and every level-0 node hung off a
 * topic with no ordering at all. The graph was therefore fully derivable from
 * {@code node_level} — one straight line, identical for every student, with the
 * profile only changing node colours. Ordering is the part of a roadmap that
 * should actually depend on who is reading it.
 *
 * <p><b>One resolver, two consumers.</b> Display order and unlock order must be
 * the same object. If the status pass kept reading {@code previous_node} while
 * the response carried computed edges, a student would see A before B and watch
 * B unlock first — a contradiction no amount of UI work can hide.
 *
 * <p><b>The regression property.</b> The final tie-break is {@code node_level}
 * then {@code node_name}, which is exactly the database ordering. A student with
 * no assessment, no skills and no market data scores every node identically and
 * therefore gets today's roadmap, node for node. Personalisation is strictly
 * additive to a working default.
 */
@Component
@RequiredArgsConstructor
public class RoadmapEdgeResolver {

    private final NodeSkillMatcher nodeSkillMatcher;

    private static final double WEIGHT_IMPORTANCE = 0.4;
    private static final double WEIGHT_DEMAND = 0.3;
    private static final double WEIGHT_READINESS = 0.3;

    /**
     * Used for demand, importance and readiness whenever the input is missing.
     * Deliberately mid-scale rather than zero: "the scraper has not run" must not
     * be read as "the market does not want this skill", which would push every
     * unmeasured node to the back of the roadmap.
     */
    private static final double NO_SIGNAL = 0.5;

    /** Difference in priority below which two nodes are treated as tied. */
    private static final double TIE_EPSILON = 0.001;

    /** Band edges for the badge. A node scoring 0.5 on every term lands NORMAL. */
    private static final double CRITICAL_AT = 0.75;
    private static final double HIGH_AT = 0.55;

    /** Percentage-point gap in market demand worth writing a reason about. */
    private static final double DEMAND_GAP_WORTH_EXPLAINING = 0.05;

    /**
     * @param visibleNodes every node the student can see; nodes filtered out
     *        earlier (unpublished, another career) are never linked, so they can
     *        never become an invisible predecessor that locks the graph
     * @param selectionView unchosen CHOOSE_ONE alternatives, which stay in the
     *        graph but are skipped over when chaining — an alternative the
     *        student did not pick must not gate the sibling they did
     */
    public ResolvedOrder resolve(List<SkillNode> visibleNodes,
                                 StudentRoadmapContext context,
                                 SelectionView selectionView) {
        if (visibleNodes == null || visibleNodes.isEmpty()) {
            return new ResolvedOrder(Map.of(), List.of(), List.of());
        }
        StudentRoadmapContext ctx = context != null ? context : StudentRoadmapContext.empty();

        // Sibling groups: the spine (no parent) plus one group per topic.
        List<SkillNode> spine = new ArrayList<>();
        Map<UUID, List<SkillNode>> childrenByParent = new LinkedHashMap<>();
        for (SkillNode node : visibleNodes) {
            if (node.getParentNode() == null) {
                spine.add(node);
            } else {
                childrenByParent
                        .computeIfAbsent(node.getParentNode().getNodeId(), k -> new ArrayList<>())
                        .add(node);
            }
        }

        Map<UUID, UUID> previousByNodeId = new HashMap<>();
        List<UUID> visitOrder = new ArrayList<>(visibleNodes.size());
        List<RoadmapEdge> edges = new ArrayList<>();

        emitGroup(spine, null, ctx, selectionView, childrenByParent, previousByNodeId, visitOrder, edges);

        // A group whose parent is not itself visible would otherwise be dropped.
        // Emit the leftovers flat rather than losing them from visitOrder, which
        // would leave their status unset and render them locked forever.
        for (Map.Entry<UUID, List<SkillNode>> orphaned : childrenByParent.entrySet()) {
            emitGroup(orphaned.getValue(), null, ctx, selectionView, Map.of(),
                    previousByNodeId, visitOrder, edges);
        }

        return new ResolvedOrder(Map.copyOf(previousByNodeId), List.copyOf(visitOrder), List.copyOf(edges));
    }

    /**
     * Sorts one sibling group, chains it, then recurses into each member's own
     * children — which is what guarantees {@code visitOrder} is dependencies-first:
     * a node is emitted only after its parent and its computed predecessor.
     *
     * <p>{@code childrenByParent} is drained as it goes, so the caller can tell
     * which groups were never reached from the spine.
     */
    private void emitGroup(List<SkillNode> group,
                           SkillNode parent,
                           StudentRoadmapContext ctx,
                           SelectionView selectionView,
                           Map<UUID, List<SkillNode>> childrenByParent,
                           Map<UUID, UUID> previousByNodeId,
                           List<UUID> visitOrder,
                           List<RoadmapEdge> edges) {
        if (group == null || group.isEmpty()) {
            return;
        }
        List<Scored> sorted = score(group, ctx);

        SkillNode chainTail = null;
        for (Scored scored : sorted) {
            SkillNode node = scored.node();
            visitOrder.add(node.getNodeId());

            if (parent != null) {
                edges.add(new RoadmapEdge(parent.getNodeId(), node.getNodeId(),
                        RoadmapEdge.KIND_HIERARCHY,
                        node.getNodeName() + " is part of " + parent.getNodeName() + "."));
            }

            // Alternatives the student did not choose stay visible but drop out of
            // the chain in both directions, so they neither gate nor get gated.
            boolean offPath = selectionView != null && selectionView.isExcludedFromProgress(node.getNodeId());
            if (!offPath) {
                if (chainTail != null) {
                    previousByNodeId.put(node.getNodeId(), chainTail.getNodeId());
                    edges.add(new RoadmapEdge(chainTail.getNodeId(), node.getNodeId(),
                            RoadmapEdge.KIND_SEQUENCE, reasonFor(chainTail, node, ctx)));
                }
                chainTail = node;
            }

            List<SkillNode> children = childrenByParent.get(node.getNodeId());
            if (children != null) {
                // Removing first stops a parent/child cycle in the data from
                // recursing forever.
                childrenByParent.remove(node.getNodeId());
                emitGroup(children, node, ctx, selectionView, childrenByParent,
                        previousByNodeId, visitOrder, edges);
            }
        }
    }

    private List<Scored> score(List<SkillNode> group, StudentRoadmapContext ctx) {
        List<Scored> scored = new ArrayList<>(group.size());
        for (SkillNode node : group) {
            scored.add(new Scored(node, isHeld(node, ctx), priority(node, ctx)));
        }
        scored.sort(Comparator
                // node_level FIRST, and as a hard constraint rather than a
                // tie-break. On the spine it encodes the pedagogical progression
                // (Internet 1 … JavaScript 4 … Authentication 13 … JOB_READY 22),
                // so letting a score cross it would tell a beginner to start at
                // Authentication and lock HTML behind it. Personalisation reorders
                // WITHIN a level, never across one — which is exactly where the
                // freedom really is: 896 of the 1.200 nodes are leaves sharing
                // level 0 under a topic.
                .comparingInt((Scored s) -> s.node().getNodeLevel() == null ? 0 : s.node().getNodeLevel())
                // "Những phần đã có thì bỏ qua": what the student already knows is
                // pulled to the front so the chain reaches their first real gap
                // immediately. Kept in the graph rather than deleted — removing it
                // would falsify the progress percentage and hide the evidence that
                // justified the skip.
                .thenComparing(Scored::held, Comparator.reverseOrder())
                .thenComparing(Scored::priority, Comparator.<Double>reverseOrder())
                // The static order, and the reason a profile-less student sees no change.
                .thenComparing((Scored s) -> s.node().getNodeName() == null ? "" : s.node().getNodeName()));
        return scored;
    }

    private boolean isHeld(SkillNode node, StudentRoadmapContext ctx) {
        if (node.getSkill() != null && ctx.proficiencyBySkillId().containsKey(node.getSkill().getSkillId())) {
            return true;
        }
        return nodeSkillMatcher.isHeld(node, ctx.heldSkillNamesLower());
    }

    private double priority(SkillNode node, StudentRoadmapContext ctx) {
        return WEIGHT_IMPORTANCE * importanceScore(node, ctx)
                + WEIGHT_DEMAND * demandScore(node, ctx)
                + WEIGHT_READINESS * readinessScore(node, ctx);
    }

    /**
     * The same score the ordering already runs on, in a form the response can
     * carry.
     *
     * <p>This number decided the student's node order from the day the resolver
     * landed, and nothing showed it. Exposing the existing computation rather
     * than adding a second one is the point: a badge that disagreed with the
     * order it sits in would be worse than no badge.
     */
    public NodePriority priorityOf(SkillNode node, StudentRoadmapContext ctx) {
        if (node == null) {
            return null;
        }
        StudentRoadmapContext safeCtx = ctx != null ? ctx : StudentRoadmapContext.empty();
        double score = priority(node, safeCtx);
        return new NodePriority(
                round(score),
                labelFor(score),
                priorityReason(node, safeCtx));
    }

    private static double round(double value) {
        return Math.round(value * 1000d) / 1000d;
    }

    private static PriorityLabel labelFor(double score) {
        if (score >= CRITICAL_AT) {
            return PriorityLabel.CRITICAL;
        }
        return score >= HIGH_AT ? PriorityLabel.HIGH : PriorityLabel.NORMAL;
    }

    /**
     * Why this node scored what it scored, in clauses that each trace back to one
     * term of the formula.
     *
     * <p>Nothing generic is emitted: a term with no signal behind it contributes
     * no clause, so a node the system knows nothing about says so by staying
     * silent rather than by claiming the market wants it.
     */
    private String priorityReason(SkillNode node, StudentRoadmapContext ctx) {
        List<String> clauses = new ArrayList<>(3);

        ImportanceLevel importance = importanceOf(node, ctx);
        if (importance != null) {
            clauses.add(switch (importance) {
                case HIGH -> "essential for this career";
                case AVG -> "recommended for this career";
                case LOW -> "optional for this career";
            });
        }

        SkillDemandResponse demand = demandOf(node, ctx);
        if (demand != null && demand.getFrequency() != null) {
            clauses.add(Math.round(demand.getFrequency() * 100) + "% of recent postings ask for it");
        }

        if (isHeld(node, ctx)) {
            clauses.add("you already have it");
        } else if (importance != null || demand != null) {
            clauses.add("you do not have it yet");
        }

        return clauses.isEmpty() ? null : String.join(", ", clauses);
    }

    /** What the response carries per node. */
    public record NodePriority(double score, PriorityLabel label, String reason) {
    }

    /** Three bands, so the badge is a judgement rather than a decimal to decode. */
    public enum PriorityLabel {
        CRITICAL, HIGH, NORMAL
    }

    private double importanceScore(SkillNode node, StudentRoadmapContext ctx) {
        ImportanceLevel importance = importanceOf(node, ctx);
        if (importance == null) {
            return NO_SIGNAL;
        }
        return switch (importance) {
            case HIGH -> 1.0;
            case AVG -> 0.6;
            case LOW -> 0.3;
        };
    }

    private ImportanceLevel importanceOf(SkillNode node, StudentRoadmapContext ctx) {
        return node.getSkill() == null ? null : ctx.importanceBySkillId().get(node.getSkill().getSkillId());
    }

    /**
     * The node's market pull, scaled against the strongest demand in this career.
     *
     * Ranks on {@code relevance} (TF-IDF), not {@code frequency}: frequency alone
     * put "Agile" and "English" above "Spring Boot" for a backend student because
     * it measures how common a term is, not how much it belongs to this career.
     * Frequency stays the number we quote to the student — "19% of postings ask
     * for it" is a sentence they can check — while relevance is what orders them.
     */
    private double demandScore(SkillNode node, StudentRoadmapContext ctx) {
        SkillDemandResponse demand = demandOf(node, ctx);
        if (demand == null || demand.getRelevance() == null || ctx.maxRelevance() <= 0) {
            return NO_SIGNAL;
        }
        return Math.min(1.0, demand.getRelevance() / ctx.maxRelevance());
    }

    private SkillDemandResponse demandOf(SkillNode node, StudentRoadmapContext ctx) {
        return node.getSkill() == null ? null : ctx.demandBySkill().get(node.getSkill().getSkillId());
    }

    /**
     * How close the node's stage sits to the student's own level: an ADVANCED
     * node is not wrong for a FRESHER, only badly timed, so this nudges rather
     * than filters.
     *
     * <p>Returns {@link #NO_SIGNAL} for everyone when the student has no level,
     * which switches the whole factor off for anyone who skipped the assessment
     * instead of guessing one for them.
     */
    private double readinessScore(SkillNode node, StudentRoadmapContext ctx) {
        if (ctx.level() == null || ctx.level() == SeniorityLevel.UNKNOWN) {
            return NO_SIGNAL;
        }
        Integer stageIndex = stageIndex(node);
        if (stageIndex == null) {
            return NO_SIGNAL;
        }
        // The five stages map onto the six rungs of the ladder: BEGINNER and
        // FRESHER both sit at FOUNDATION, because the difference between them is
        // how much of it they have done, not which stage they belong in.
        int target = switch (ctx.level()) {
            case BEGINNER, FRESHER -> 0;
            case JUNIOR -> 1;
            case MID -> 2;
            case SENIOR -> 3;
            case EXPERT, UNKNOWN -> 4;
        };
        return 1.0 - Math.abs(stageIndex - target) / 4.0;
    }

    private Integer stageIndex(SkillNode node) {
        if (node.getType() == null || node.getType().getStage() == null) {
            return null;
        }
        StageType stage = node.getType().getStage();
        return switch (stage) {
            case FOUNDATION -> 0;
            case CORE -> 1;
            case PRACTICAL -> 2;
            case ADVANCED -> 3;
            case JOB_READY -> 4;
        };
    }

    /**
     * One sentence naming why {@code before} comes first. This is the visible
     * proof that the ordering was decided by something, and it is written in the
     * student's language because it is shown to them, not logged.
     */
    private String reasonFor(SkillNode before, SkillNode after, StudentRoadmapContext ctx) {
        if (isHeld(before, ctx) && !isHeld(after, ctx)) {
            return "You already have " + before.getNodeName() + ", so it is placed first and your path "
                    + "starts at " + after.getNodeName() + ".";
        }

        SkillDemandResponse beforeDemand = demandOf(before, ctx);
        SkillDemandResponse afterDemand = demandOf(after, ctx);
        double beforeFrequency = beforeDemand != null && beforeDemand.getFrequency() != null
                ? beforeDemand.getFrequency() : -1;
        double afterFrequency = afterDemand != null && afterDemand.getFrequency() != null
                ? afterDemand.getFrequency() : -1;
        if (beforeFrequency >= 0 && afterFrequency >= 0
                && beforeFrequency - afterFrequency >= DEMAND_GAP_WORTH_EXPLAINING) {
            return before.getNodeName() + " before " + after.getNodeName() + " — "
                    + percent(beforeFrequency) + " of recent postings ask for " + before.getNodeName()
                    + ", against " + percent(afterFrequency) + " for " + after.getNodeName() + ".";
        }

        ImportanceLevel beforeImportance = importanceOf(before, ctx);
        if (beforeImportance == ImportanceLevel.HIGH && importanceOf(after, ctx) != ImportanceLevel.HIGH) {
            return before.getNodeName() + " is a required skill for your target role, so it comes "
                    + "before " + after.getNodeName() + ".";
        }

        if (Math.abs(priority(before, ctx) - priority(after, ctx)) < TIE_EPSILON) {
            return "No data separates these two, so the roadmap's default order is kept.";
        }
        return before.getNodeName() + " is placed before " + after.getNodeName()
                + " by importance to your role, market demand and your current level.";
    }

    private String percent(double frequency) {
        return Math.round(frequency * 100) + "%";
    }

    private record Scored(SkillNode node, boolean held, double priority) {
    }
}
