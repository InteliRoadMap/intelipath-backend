package com.inteliroadmap.backend.components;

import com.fasterxml.jackson.databind.JsonNode;
import com.inteliroadmap.backend.domain.dto.response.market.SkillDemandResponse;
import com.inteliroadmap.backend.domain.dto.response.plan.LearningPlanResponse;
import com.inteliroadmap.backend.domain.dto.response.plan.PlanNodeResponse;
import com.inteliroadmap.backend.domain.dto.response.plan.PlanSkipResponse;
import com.inteliroadmap.backend.domain.dto.response.plan.PlanStepResponse;
import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
import com.inteliroadmap.backend.domain.enums.SeniorityLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Derives what a student should learn next from what they can prove and what the
 * market is asking for.
 *
 * <p>The roadmap endpoint answers "what exists in this career, filtered". That is
 * a catalog view, and no amount of filtering turns a catalog into advice. This
 * runs the other way:
 *
 * <ol>
 *   <li>Start from the target role's required skills and how much it needs each.</li>
 *   <li>Subtract what the student already holds, at the proficiency they hold it.</li>
 *   <li>Rank what is left by importance, live market demand, and how close it
 *       sits to their assessed level.</li>
 *   <li>Only then look in the node catalog, for something to actually read.</li>
 * </ol>
 *
 * <p>Every step carries the sentence that justifies it, numbers included, because
 * a plan a student cannot argue with is not a plan — it is an instruction.
 */
@Component
public class PersonalizedPlanBuilder {

    /** Proficiency at or above which a required skill counts as covered. */
    private static final short COVERED_AT = 3; // APPLIED

    private static final double WEIGHT_IMPORTANCE = 0.45;
    private static final double WEIGHT_DEMAND = 0.35;
    private static final double WEIGHT_READINESS = 0.20;

    /**
     * Used whenever a signal is missing. Mid-scale, never zero: an unmeasured
     * skill must not be ranked as though the market had rejected it.
     */
    private static final double NO_SIGNAL = 0.5;

    /** How many steps a plan holds. Short on purpose — a plan of forty is a catalog again. */
    private static final int DEFAULT_STEP_COUNT = 6;

    /** Nodes offered per step. Enough to start, not enough to bury the step. */
    private static final int NODES_PER_STEP = 4;

    /** Number of stage bands, used to pack depth and stage into one rank. */
    private static final int STAGE_SPAN = 5;

    /** Depth beyond which everything is equally deep for ordering purposes. */
    private static final int MAX_DEPTH = 6;

    /** Stage for a node that declares none: mid-ladder, neither promoted nor buried. */
    private static final int DEFAULT_STAGE = 2;

    /** Rank for a skill nothing teaches — same neutral position, one band in. */
    private static final int DEFAULT_RANK = STAGE_SPAN + DEFAULT_STAGE;

    private static final String LOCKED = "locked";

    public LearningPlanResponse build(String careerName,
                                      SeniorityLevel level,
                                      List<CareerRequiredSkill> requiredSkills,
                                      List<StudentSkill> studentSkills,
                                      List<SkillNode> careerNodes,
                                      Map<UUID, SkillDemandResponse> demandBySkill,
                                      Map<UUID, String> statusByNodeId) {

        Map<UUID, StudentSkill> heldBySkillId = new HashMap<>();
        for (StudentSkill held : studentSkills) {
            if (held.getSkill() != null) {
                heldBySkillId.put(held.getSkill().getSkillId(), held);
            }
        }

        List<Scored> gaps = new ArrayList<>();
        List<PlanSkipResponse> covered = new ArrayList<>();

        for (CareerRequiredSkill required : requiredSkills) {
            Skill skill = required.getSkill();
            if (skill == null || skill.getSkillId() == null) {
                continue;
            }
            StudentSkill held = heldBySkillId.get(skill.getSkillId());
            Short proficiency = held == null ? null : held.getProficiency();

            if (proficiency != null && proficiency >= COVERED_AT) {
                covered.add(PlanSkipResponse.builder()
                        .skillId(skill.getSkillId())
                        .skillName(skill.getSkillName())
                        .proficiency(proficiency)
                        .verifiedBy(held.getVerifiedBy())
                        .build());
                continue;
            }
            gaps.add(new Scored(skill, required.getImportanceLevel(), proficiency,
                    demandBySkill.get(skill.getSkillId()), level));
        }

        Map<UUID, List<SkillNode>> nodesBySkillId = new HashMap<>();
        Map<String, List<SkillNode>> nodesByName = new HashMap<>();
        for (SkillNode node : careerNodes) {
            if (node.getSkill() != null && node.getSkill().getSkillId() != null) {
                nodesBySkillId.computeIfAbsent(node.getSkill().getSkillId(), k -> new ArrayList<>()).add(node);
            }
            // Second index, by normalised name, so a skill still finds its material
            // when the catalog and the roadmap spell it differently.
            for (String key : matchKeys(node.getSkill() != null ? node.getSkill().getSkillName() : null)) {
                nodesByName.computeIfAbsent(key, k -> new ArrayList<>()).add(node);
            }
            for (String key : matchKeys(node.getNodeName())) {
                nodesByName.computeIfAbsent(key, k -> new ArrayList<>()).add(node);
            }
        }

        // Prerequisite order is a HARD constraint, not a weight. The node tree
        // already carries it in `node_types.stage`, and nobody was reading it —
        // which is why this plan told a FRESHER to learn GraphQL before
        // JavaScript, with the two separated only by the alphabet. No amount of
        // market demand may reorder across stages, for the same reason node_level
        // is a hard constraint in RoadmapEdgeResolver.
        Map<UUID, Integer> rankBySkillId = new HashMap<>();
        for (Scored gap : gaps) {
            rankBySkillId.put(gap.skill().getSkillId(),
                    prerequisiteRank(nodesFor(gap.skill(), nodesBySkillId, nodesByName)));
        }
        gaps.sort(Comparator
                .comparingInt((Scored g) -> rankBySkillId.getOrDefault(g.skill().getSkillId(), DEFAULT_RANK))
                .thenComparing(Comparator.comparingDouble(Scored::score).reversed())
                // Stable and predictable when nothing separates two skills.
                .thenComparing(g -> g.skill().getSkillName() == null ? "" : g.skill().getSkillName()));

        List<PlanStepResponse> steps = new ArrayList<>();
        for (Scored gap : gaps) {
            if (steps.size() >= DEFAULT_STEP_COUNT) {
                break;
            }
            List<PlanNodeResponse> nodes = materialFor(
                    nodesFor(gap.skill(), nodesBySkillId, nodesByName), statusByNodeId);
            // A skill with nothing to read is a gap we cannot act on yet. Naming it
            // without offering a next move would be noise, so it waits for content.
            if (nodes.isEmpty()) {
                continue;
            }
            // Nor may the plan propose work the roadmap would refuse to unlock —
            // that is the plan contradicting the product it belongs to.
            if (nodes.stream().allMatch(n -> LOCKED.equals(n.getStatus()))) {
                continue;
            }
            steps.add(PlanStepResponse.builder()
                    .order(steps.size() + 1)
                    .skillId(gap.skill().getSkillId())
                    .skillName(gap.skill().getSkillName())
                    .importance(gap.importance() == null ? null : gap.importance().name())
                    .marketDemand(gap.demand())
                    .currentProficiency(gap.proficiency())
                    .why(explain(gap))
                    .nodes(nodes)
                    .build());
        }

        return LearningPlanResponse.builder()
                .targetCareerRole(careerName)
                .level(level == null ? null : level.name())
                .requiredSkillCount(requiredSkills.size())
                .coveredSkillCount(covered.size())
                .summary(summarise(careerName, level, requiredSkills.size(), covered.size(), steps))
                .steps(steps)
                .alreadyCovered(covered)
                .build();
    }

    /** Nodes to read for a skill: unfinished first, and only ones that carry links. */
    private List<PlanNodeResponse> materialFor(List<SkillNode> nodes, Map<UUID, String> statusByNodeId) {
        List<PlanNodeResponse> out = new ArrayList<>();
        List<SkillNode> ordered = new ArrayList<>(nodes);
        ordered.sort(Comparator
                .comparing((SkillNode n) -> "completed".equals(statusByNodeId.get(n.getNodeId())))
                .thenComparing(n -> n.getNodeLevel() == null ? 0 : n.getNodeLevel())
                .thenComparing(n -> n.getNodeName() == null ? "" : n.getNodeName()));

        for (SkillNode node : ordered) {
            if (out.size() >= NODES_PER_STEP) {
                break;
            }
            List<String> resources = resourcesOf(node);
            if (resources.isEmpty() && (node.getDescription() == null || node.getDescription().isBlank())) {
                continue;
            }
            out.add(PlanNodeResponse.builder()
                    .nodeId(node.getNodeId())
                    .nodeName(node.getNodeName())
                    .description(node.getDescription())
                    .status(statusByNodeId.getOrDefault(node.getNodeId(), "current"))
                    .resources(resources)
                    .build());
        }
        return out;
    }

    /**
     * The nodes that teach a skill, by id first and by name second.
     *
     * <p>Measured on the real Frontend data, 43 of the 115 core skills had no node
     * by id — and thirteen of those were the HIGH ones a student most needs:
     * {@code CSS3}, {@code HTML5}, {@code TypeScript}, {@code Git and Version
     * Control}. They are all taught; the catalog and the roadmap simply spell
     * them differently. Matching by id alone silently dropped them from the plan.
     */
    private List<SkillNode> nodesFor(Skill skill,
                                     Map<UUID, List<SkillNode>> byId,
                                     Map<String, List<SkillNode>> byName) {
        List<SkillNode> exact = byId.get(skill.getSkillId());
        if (exact != null && !exact.isEmpty()) {
            return exact;
        }
        for (String key : matchKeys(skill.getSkillName())) {
            List<SkillNode> matched = byName.get(key);
            if (matched != null && !matched.isEmpty()) {
                return matched;
            }
        }
        return List.of();
    }

    /**
     * The forms a skill name might be written in, most specific first.
     *
     * <p>Covers the three ways the two vocabularies actually diverge: a version
     * suffix ({@code CSS3} / {@code CSS}), a trailing qualifier ({@code Frontend
     * Testing} / {@code Testing}), and a conjunction naming two things at once
     * ({@code Git and Version Control}).
     */
    private List<String> matchKeys(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        String lower = name.trim().toLowerCase();
        addKey(keys, lower);
        // Drop a trailing version number: css3 -> css, html5 -> html, vue3 -> vue.
        addKey(keys, lower.replaceAll("\\s*\\d+$", ""));
        // Split a conjunction and offer each half.
        for (String part : lower.split("\\s+(and|&|/)\\s+")) {
            addKey(keys, part);
        }
        return keys;
    }

    /** Normalises to letters and digits only, and refuses keys too short to be safe. */
    private void addKey(List<String> keys, String raw) {
        String key = raw == null ? "" : raw.replaceAll("[^a-z0-9+#]", "");
        // Two characters is where real names live (Go, C#, R) and where accidental
        // collisions start; anything shorter matches half the catalog.
        if (key.length() >= 2 && !keys.contains(key)) {
            keys.add(key);
        }
    }

    /**
     * How early a skill sits in the curriculum.
     *
     * <p>Measured from the <b>shallowest</b> node that teaches it, because depth
     * in the tree is what actually separates a backbone skill from a specialism.
     * Measured on the real Frontend data: {@code HTML} and {@code JavaScript} own
     * top-level topics, while {@code React} and {@code Next.js} appear only as
     * children inside other topics — which is exactly the ordering a beginner
     * needs, and it is invisible to any other signal.
     *
     * <p>Stage breaks ties within a depth. Stage alone cannot do this job: every
     * skill in the table has at least one FOUNDATION node somewhere, so ranking
     * by "earliest stage" collapsed every skill to 0 and left the alphabet
     * deciding the plan.
     *
     * @return depth×{@value #STAGE_SPAN} + stage, so depth dominates and stage
     *         orders within it
     */
    private int prerequisiteRank(List<SkillNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return DEFAULT_RANK;
        }
        int bestDepth = Integer.MAX_VALUE;
        int bestStage = DEFAULT_STAGE;
        for (SkillNode node : nodes) {
            int depth = depthOf(node);
            int stage = stageOf(node);
            if (depth < bestDepth || (depth == bestDepth && stage < bestStage)) {
                bestDepth = depth;
                bestStage = stage;
            }
        }
        return bestDepth == Integer.MAX_VALUE
                ? DEFAULT_RANK
                : Math.min(bestDepth, MAX_DEPTH) * STAGE_SPAN + bestStage;
    }

    /** Distance from a root; the cap stops a cycle in the data from hanging the request. */
    private int depthOf(SkillNode node) {
        int depth = 0;
        for (SkillNode cursor = node.getParentNode(); cursor != null && depth < 32;
             cursor = cursor.getParentNode()) {
            depth++;
        }
        return depth;
    }

    /** FOUNDATION 0 … JOB_READY 4, or mid-scale when the node declares no stage. */
    private int stageOf(SkillNode node) {
        if (node.getType() == null || node.getType().getStage() == null) {
            return DEFAULT_STAGE;
        }
        return switch (node.getType().getStage()) {
            case FOUNDATION -> 0;
            case CORE -> 1;
            case PRACTICAL -> 2;
            case ADVANCED -> 3;
            case JOB_READY -> 4;
        };
    }

    private List<String> resourcesOf(SkillNode node) {
        JsonNode resource = node.getResource();
        if (resource == null || !resource.isArray()) {
            return List.of();
        }
        List<String> links = new ArrayList<>();
        for (JsonNode entry : resource) {
            String url = entry.isTextual() ? entry.asText("") : entry.path("url").asText("");
            if (url != null && !url.isBlank()) {
                links.add(url);
            }
        }
        return links;
    }

    /**
     * The sentence a student reads to decide whether they agree.
     *
     * <p>Built from whichever evidence actually exists rather than from a
     * template with blanks: claiming market demand when the scrape is too thin to
     * report any would be inventing the strongest part of the argument.
     */
    private String explain(Scored gap) {
        String name = gap.skill().getSkillName();
        StringBuilder why = new StringBuilder();

        if (gap.importance() == ImportanceLevel.HIGH) {
            why.append(name).append(" is a required skill for your target role");
        } else if (gap.importance() == ImportanceLevel.AVG) {
            why.append(name).append(" is commonly asked for in this role");
        } else {
            why.append(name).append(" belongs to this role's skill set");
        }

        SkillDemandResponse demand = gap.demand();
        if (demand != null && demand.getJobCount() != null && demand.getSampleSize() != null) {
            why.append(", appearing in ").append(demand.getJobCount()).append(" of ")
                    .append(demand.getSampleSize()).append(" recent postings");
        }

        if (gap.proficiency() != null) {
            why.append(". You have touched it but only reached ")
                    .append(proficiencyLabel(gap.proficiency()))
                    .append(", which is not yet enough to count as covered");
        } else {
            why.append(". Nothing in your profile evidences this skill yet");
        }
        return why.append('.').toString();
    }

    private String proficiencyLabel(short proficiency) {
        return switch (proficiency) {
            case 1 -> "AWARE";
            case 2 -> "PRACTICED";
            case 3 -> "APPLIED";
            case 4 -> "PROFESSIONAL";
            default -> "unknown";
        };
    }

    private String summarise(String careerName, SeniorityLevel level,
                             int required, int covered, List<PlanStepResponse> steps) {
        if (steps.isEmpty()) {
            return covered >= required && required > 0
                    ? "You cover all " + required + " required skills for " + careerName
                            + ". Go deeper rather than wider."
                    : "Not enough about you yet to build a plan. Take the assessment or connect "
                            + "GitHub so the system knows where you stand.";
        }
        String levelPart = level == null
                ? "You have not taken the assessment, so this order leans mostly on market demand"
                : "At " + level.name() + ", this is the work worth doing next";
        return levelPart + ". You cover " + covered + " of " + required
                + " required skills for " + careerName + ".";
    }

    /** One gap skill with everything needed to rank and explain it. */
    private record Scored(Skill skill,
                          ImportanceLevel importance,
                          Short proficiency,
                          SkillDemandResponse demand,
                          SeniorityLevel level) {

        double score() {
            return WEIGHT_IMPORTANCE * importanceScore()
                    + WEIGHT_DEMAND * demandScore()
                    + WEIGHT_READINESS * readinessScore();
        }

        private double importanceScore() {
            if (importance == null) return NO_SIGNAL;
            return switch (importance) {
                case HIGH -> 1.0;
                case AVG -> 0.6;
                case LOW -> 0.3;
            };
        }

        private double demandScore() {
            return demand == null || demand.getFrequency() == null ? NO_SIGNAL : demand.getFrequency();
        }

        /**
         * A skill already half-known is cheaper to finish than one started from
         * nothing, so it earns a nudge up the order. With no prior claim at all
         * this contributes nothing either way.
         */
        private double readinessScore() {
            if (proficiency == null) return NO_SIGNAL;
            return Math.min(1.0, proficiency / (double) COVERED_AT);
        }
    }
}
