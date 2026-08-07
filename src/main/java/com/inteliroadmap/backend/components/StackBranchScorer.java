package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.dto.response.market.SkillDemandResponse;
import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Picks which branch of a CHOOSE_ONE group fits a student, and says why.
 *
 * <p><b>Why not name matching.</b> The selection service used to auto-pick by
 * comparing the alternative's own name against the student's declared skills,
 * accepting only an exact single match. On the real Backend catalog that reads
 * the wrong branch: inside "Pick a Language", the node named {@code JavaScript}
 * carries {@code skill_id} pointing at the skill <em>"Pick a Language"</em> and
 * has no children, while {@code JavaScript (Node.js)} is the one linked to the
 * JavaScript skill. A student who declares JavaScript matched the dead node by
 * name and got a branch with nothing in it.
 *
 * <p><b>What it scores instead.</b> The whole subtree under each alternative,
 * because a branch is worth what it teaches, not what it is called:
 *
 * <pre>
 *   score(branch) = Σ over distinct skills in the subtree of
 *                     proficiency(1..4) × relevance(skill, career) × evidenceWeight
 * </pre>
 *
 * <p>{@code evidenceWeight} is 2 for a skill with {@code verified_by} set and 1
 * otherwise. A repository the system read outranks a claim the student typed,
 * which is the same rule {@code SkillEvidenceService} applies when the two
 * disagree — this is that rule reaching the roadmap.
 *
 * <p><b>What it refuses to do.</b> A tie is left alone. Two branches a student
 * has equal grounds for is not a decision the system is entitled to make on
 * their behalf, and guessing would be worse than the empty state: they would
 * have to notice a wrong pick to undo it.
 */
@Component
@Slf4j
public class StackBranchScorer {

    /** Objective evidence counts double against a self-report of the same skill. */
    private static final double VERIFIED_WEIGHT = 2.0;
    private static final double DECLARED_WEIGHT = 1.0;

    /**
     * Scores below this are treated as no signal at all.
     *
     * <p>Not zero: relevance is a small positive number for almost every skill,
     * so an exact-zero test would let a branch win on a rounding artefact of a
     * skill the student holds at AWARE.
     */
    private static final double MIN_SCORE = 0.001;

    /**
     * How far ahead the winner must be, as a share of its own score.
     *
     * <p>Two branches within 10% of each other are a tie in everything but
     * arithmetic, and the student is better served choosing.
     */
    private static final double DECISIVE_MARGIN = 0.10;

    /** Skills to name in the reason before it stops being a sentence. */
    private static final int REASONS_NAMED = 2;

    /**
     * @param chosen the winning alternative
     * @param score its score
     * @param runnerUpScore the next best, for the margin test that already passed
     * @param reason why, in the student's own numbers
     */
    public record BranchVerdict(SkillNode chosen, double score, double runnerUpScore, String reason) {
    }

    /** Why the scorer did or did not name a winner. */
    public enum Verdict {
        /** One branch is clear of the next by more than {@link #DECISIVE_MARGIN}. */
        DECISIVE,
        /** Two branches are within the margin; the student decides. */
        TOO_CLOSE,
        /** Nothing the student holds appears in any branch. */
        NO_SIGNAL
    }

    /**
     * The full ranking, winner or not.
     *
     * @param verdict whether {@code ranked.get(0)} may be presented as the pick
     * @param ranked every alternative, best first
     */
    public record Ranking(Verdict verdict, List<Scored> ranked) {
    }

    /**
     * Score every alternative and say whether the top one is decisive.
     *
     * <p>The ranking existed all along inside {@link #pick} and was discarded
     * except for the winner, which meant the roadmap could act on a fit score it
     * could never show. Both callers now read the same list, so what a student is
     * told about their options cannot drift from what the system did with them.
     *
     * @param alternatives the group's children
     * @param childrenByParent every node of the career, indexed by parent, so the
     *        subtree walk never has to touch the database
     * @param heldBySkillId what the student holds, from {@code student_skills}
     * @param demandBySkill market relevance per skill for this career
     */
    public Ranking rank(List<SkillNode> alternatives,
                        Map<UUID, List<SkillNode>> childrenByParent,
                        Map<UUID, StudentSkill> heldBySkillId,
                        Map<UUID, SkillDemandResponse> demandBySkill) {
        if (alternatives == null || alternatives.isEmpty()) {
            return new Ranking(Verdict.NO_SIGNAL, List.of());
        }

        List<Scored> scored = new ArrayList<>(alternatives.size());
        for (SkillNode alternative : alternatives) {
            scored.add(scoreBranch(alternative, childrenByParent, heldBySkillId, demandBySkill));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());

        // A group of one is not a decision, so there is nothing to be decisive
        // about — but the single branch is still returned, scored, because the
        // caller may want to show it.
        if (scored.size() < 2 || scored.get(0).score() < MIN_SCORE) {
            return new Ranking(Verdict.NO_SIGNAL, scored);
        }
        if (scored.get(1).score() > scored.get(0).score() * (1 - DECISIVE_MARGIN)) {
            log.debug("StackBranchScorer: '{}' ({}) and '{}' ({}) are too close to call; leaving the choice open.",
                    scored.get(0).node().getNodeName(), scored.get(0).score(),
                    scored.get(1).node().getNodeName(), scored.get(1).score());
            return new Ranking(Verdict.TOO_CLOSE, scored);
        }
        return new Ranking(Verdict.DECISIVE, scored);
    }

    /**
     * @return the branch to pick, or null when nothing decisive came out
     */
    public BranchVerdict pick(List<SkillNode> alternatives,
                              Map<UUID, List<SkillNode>> childrenByParent,
                              Map<UUID, StudentSkill> heldBySkillId,
                              Map<UUID, SkillDemandResponse> demandBySkill) {
        if (alternatives == null || alternatives.size() < 2) {
            return null;
        }
        Ranking ranking = rank(alternatives, childrenByParent, heldBySkillId, demandBySkill);
        if (ranking.verdict() != Verdict.DECISIVE) {
            return null;
        }
        Scored winner = ranking.ranked().get(0);
        Scored runnerUp = ranking.ranked().get(1);
        return new BranchVerdict(winner.node(), winner.score(), runnerUp.score(),
                reasonFor(winner, runnerUp));
    }

    private Scored scoreBranch(SkillNode branch,
                               Map<UUID, List<SkillNode>> childrenByParent,
                               Map<UUID, StudentSkill> heldBySkillId,
                               Map<UUID, SkillDemandResponse> demandBySkill) {
        double total = 0;
        List<Contribution> contributions = new ArrayList<>();
        Set<UUID> countedSkills = new HashSet<>();

        for (SkillNode node : subtreeOf(branch, childrenByParent)) {
            if (node.getSkill() == null || node.getSkill().getSkillId() == null) {
                continue;
            }
            UUID skillId = node.getSkill().getSkillId();
            // Distinct skills, not nodes: the Redis branch mentions Caching on
            // nine of its children, and counting each would let a wide branch beat
            // a deep one on repetition alone.
            if (!countedSkills.add(skillId)) {
                continue;
            }

            StudentSkill held = heldBySkillId.get(skillId);
            if (held == null || held.getProficiency() == null || held.getProficiency() <= 0) {
                continue;
            }

            SkillDemandResponse demand = demandBySkill.get(skillId);
            Double relevance = demand == null ? null : demand.getRelevance();
            if (relevance == null || relevance <= 0) {
                continue;
            }

            boolean verified = held.getVerifiedBy() != null && !held.getVerifiedBy().isBlank();
            double weight = verified ? VERIFIED_WEIGHT : DECLARED_WEIGHT;
            double contribution = held.getProficiency() * relevance * weight;
            total += contribution;
            contributions.add(new Contribution(
                    node.getSkill().getSkillName(), held.getProficiency(), verified, contribution));
        }

        contributions.sort(Comparator.comparingDouble(Contribution::value).reversed());
        return new Scored(branch, total, contributions);
    }

    /** The branch and everything under it, cycle-safe. */
    private List<SkillNode> subtreeOf(SkillNode root, Map<UUID, List<SkillNode>> childrenByParent) {
        List<SkillNode> collected = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        Deque<SkillNode> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            SkillNode node = pending.pop();
            if (node.getNodeId() == null || !seen.add(node.getNodeId())) {
                continue;   // a parent/child cycle in the data must not hang the request
            }
            collected.add(node);
            for (SkillNode child : childrenByParent.getOrDefault(node.getNodeId(), List.of())) {
                pending.push(child);
            }
        }
        return collected;
    }

    /**
     * The sentence the student reads, built only from things that actually
     * happened: which of their own skills carried the branch, whether each was
     * verified, and what it beat.
     */
    private String reasonFor(Scored winner, Scored runnerUp) {
        StringBuilder sentence = new StringBuilder("Chose ")
                .append(winner.node().getNodeName());

        List<Contribution> named = winner.contributions().stream().limit(REASONS_NAMED).toList();
        if (!named.isEmpty()) {
            sentence.append(" because you already have ");
            for (int i = 0; i < named.size(); i++) {
                if (i > 0) {
                    sentence.append(" and ");
                }
                Contribution c = named.get(i);
                sentence.append(c.skillName())
                        .append(" at ")
                        .append(proficiencyName(c.proficiency()));
                if (c.verified()) {
                    sentence.append(" (verified)");
                }
            }
        }

        sentence.append(", ahead of ").append(runnerUp.node().getNodeName()).append('.');
        return sentence.toString();
    }

    private String proficiencyName(int proficiency) {
        return switch (proficiency) {
            case 1 -> "AWARE";
            case 2 -> "PRACTICED";
            case 3 -> "APPLIED";
            case 4 -> "PROFESSIONAL";
            default -> "level " + proficiency;
        };
    }

    /** One alternative and what the student already brings to it, best first. */
    public record Scored(SkillNode node, double score, List<Contribution> contributions) {
    }

    /** One skill of the student's that counted towards a branch, and by how much. */
    public record Contribution(String skillName, int proficiency, boolean verified, double value) {
    }

    /** Convenience for callers holding a flat node list. */
    public static Map<UUID, List<SkillNode>> indexByParent(Collection<SkillNode> nodes) {
        Map<UUID, List<SkillNode>> byParent = new java.util.HashMap<>();
        for (SkillNode node : nodes) {
            if (node.getParentNode() != null && node.getParentNode().getNodeId() != null) {
                byParent.computeIfAbsent(node.getParentNode().getNodeId(), k -> new ArrayList<>()).add(node);
            }
        }
        for (List<SkillNode> siblings : byParent.values()) {
            siblings.sort(Comparator.comparing(SkillNode::getNodeName,
                    Comparator.nullsLast(Comparator.comparing(n -> n.toLowerCase(Locale.ROOT)))));
        }
        return byParent;
    }
}
