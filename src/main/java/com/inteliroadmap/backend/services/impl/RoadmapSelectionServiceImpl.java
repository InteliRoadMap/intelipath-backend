package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.response.roadmap.MatchedSkillResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.inteliroadmap.backend.components.StackBranchScorer;
import com.inteliroadmap.backend.domain.dto.request.SelectAlternativeRequest;
import com.inteliroadmap.backend.domain.dto.response.market.SkillDemandResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.ChoiceOptionResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.ChoiceOptionsResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.NodeSelectionResponse;
import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentNodeSelection;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.exceptions.BadRequestException;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.SkillNodeRepository;
import com.inteliroadmap.backend.repositories.SkillRepository;
import com.inteliroadmap.backend.repositories.StudentNodeSelectionRepository;
import com.inteliroadmap.backend.repositories.StudentSkillRepository;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.services.MarketDemandService;
import com.inteliroadmap.backend.services.RoadmapSelectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of {@link RoadmapSelectionService}.
 *
 * Selection history note: switching a choice does NOT delete the old branch's
 * student_progress rows. Completed work stays recorded; the roadmap read path
 * simply stops counting the now-unchosen branch, so switching back later
 * restores the earlier progress.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoadmapSelectionServiceImpl implements RoadmapSelectionService {

    private static final String CHOOSE_ONE = "CHOOSE_ONE";

    /** Skills to name per option before the chip row stops being readable. */
    private static final int MATCHED_SKILLS_SHOWN = 3;

    private final AuthenticatedStudentService authenticatedStudentService;
    private final StudentNodeSelectionRepository selectionRepository;
    private final SkillNodeRepository skillNodeRepository;
    private final SkillRepository skillRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final StackBranchScorer stackBranchScorer;
    private final MarketDemandService marketDemandService;

    @Transactional
    @Override
    public NodeSelectionResponse selectAlternative(SelectAlternativeRequest request) {
        log.info("RoadmapSelectionServiceImpl: Select alternative request received. group: {}, chosen: {}",
                request.getGroupNodeId(), request.getChosenNodeId());

        Student student = authenticatedStudentService.getRequiredStudent();
        SkillNode group = getChoiceGroupOwnedByStudentCareer(request.getGroupNodeId(), student);

        SkillNode chosen = skillNodeRepository.findById(request.getChosenNodeId())
                .orElseThrow(() -> new ResourceNotFoundException("Chosen node not found"));
        if (chosen.getParentNode() == null
                || !group.getNodeId().equals(chosen.getParentNode().getNodeId())) {
            throw new BadRequestException("Chosen node is not an alternative of the given group");
        }

        // Upsert on (student, group): re-picking switches the stored choice.
        StudentNodeSelection selection = selectionRepository
                .findByStudent_UserIdAndGroupNode_NodeId(student.getUserId(), group.getNodeId())
                .orElseGet(() -> StudentNodeSelection.builder()
                        .student(Student.builder().userId(student.getUserId()).build())
                        .groupNode(group)
                        .build());
        selection.setChosenNode(chosen);
        // The student has now decided, so the machine's reasoning no longer
        // describes this row. Leaving it would show them a justification for a
        // branch they overruled.
        selection.setAutoSelected(Boolean.FALSE);
        selection.setAutoReason(null);
        selection = selectionRepository.save(selection);

        log.info("RoadmapSelectionServiceImpl: Student {} chose '{}' in group '{}'",
                student.getUserId(), chosen.getNodeName(), group.getNodeName());
        return toResponse(selection, group, chosen);
    }

    @Transactional(readOnly = true)
    @Override
    public List<NodeSelectionResponse> getSelections() {
        Student student = authenticatedStudentService.getRequiredStudent();
        return selectionRepository.findByStudent_UserId(student.getUserId()).stream()
                .map(selection -> toResponse(selection, selection.getGroupNode(), selection.getChosenNode()))
                .toList();
    }

    @Transactional
    @Override
    public void clearSelection(UUID groupNodeId) {
        log.info("RoadmapSelectionServiceImpl: Clear selection request received. group: {}", groupNodeId);

        Student student = authenticatedStudentService.getRequiredStudent();
        StudentNodeSelection selection = selectionRepository
                .findByStudent_UserIdAndGroupNode_NodeId(student.getUserId(), groupNodeId)
                .orElseThrow(() -> new ResourceNotFoundException("No selection stored for this group"));
        selectionRepository.delete(selection);
    }

    /**
     * The group's options, ranked by the same scorer that auto-selects.
     *
     * <p>Read-only on purpose: a student opening the chooser must not have a
     * choice stored for them as a side effect of looking. The ranking shown here
     * and the pick {@link #autoDefaultSelections()} would make come from one call
     * to {@link StackBranchScorer#rank}, so they cannot say different things.
     */
    @Transactional(readOnly = true)
    @Override
    public ChoiceOptionsResponse getOptions(UUID groupNodeId) {
        Student student = authenticatedStudentService.getRequiredStudent();
        SkillNode group = getChoiceGroupOwnedByStudentCareer(groupNodeId, student);

        List<SkillNode> careerNodes = skillNodeRepository
                .findPublishedForCareer(student.getCareerRole().getCareerId());
        Map<UUID, List<SkillNode>> childrenByParent = StackBranchScorer.indexByParent(careerNodes);
        List<SkillNode> alternatives = childrenByParent.getOrDefault(groupNodeId, List.of());

        Map<UUID, StudentSkill> heldBySkillId = new HashMap<>();
        for (StudentSkill studentSkill : studentSkillRepository.findByStudent_UserId(student.getUserId())) {
            if (studentSkill.getSkill() != null && studentSkill.getSkill().getSkillId() != null) {
                heldBySkillId.put(studentSkill.getSkill().getSkillId(), studentSkill);
            }
        }

        // Losing the market table costs the bars, not the chooser: fit still
        // ranks, and an option with no demand data says so rather than showing 0.
        Map<UUID, SkillDemandResponse> demandBySkill;
        try {
            demandBySkill = marketDemandService.demandBySkill(student.getCareerRole().getCareerId());
        } catch (Exception e) {
            log.warn("RoadmapSelectionServiceImpl: no market demand for career {}: {}",
                    student.getCareerRole().getCareerId(), e.getMessage());
            demandBySkill = Map.of();
        }

        // Displayed figures come from the raw counts, not from the relevance map
        // the scorer uses. Relevance measures how characteristic a skill is of
        // this career, so Go — 39 postings, but named by half the careers — used to
        // fall under the relevance gate and drop out entirely; the chooser then told
        // the student "No posting data" about a skill with 39 postings behind it.
        // Ranking still runs on relevance: what to recommend and what to report
        // are different questions and deserve different numbers.
        //
        // The gate itself has since moved onto weighted demand (MIN_WEIGHTED_DEMAND),
        // so a widely-wanted skill is no longer deleted from the payload for being
        // widely wanted. This call stays regardless: it is the display path, and it
        // is not subject to any career's catalog or grading.
        Map<UUID, SkillDemandResponse> displayDemand;
        try {
            displayDemand = marketDemandService.rawDemandBySkill();
        } catch (Exception e) {
            log.warn("RoadmapSelectionServiceImpl: no raw demand available: {}", e.getMessage());
            displayDemand = Map.of();
        }

        StackBranchScorer.Ranking ranking =
                stackBranchScorer.rank(alternatives, childrenByParent, heldBySkillId, demandBySkill);

        UUID chosenNodeId = null;
        boolean autoSelected = false;
        StudentNodeSelection stored = selectionRepository
                .findByStudent_UserIdAndGroupNode_NodeId(student.getUserId(), groupNodeId)
                .orElse(null);
        if (stored != null && stored.getChosenNode() != null) {
            chosenNodeId = stored.getChosenNode().getNodeId();
            autoSelected = Boolean.TRUE.equals(stored.getAutoSelected());
        }

        // Scaled against the strongest option in the group, not against 1.0: "the
        // best of these nine" is the question a chooser asks. Guarded so a group
        // where nobody scored does not divide by zero and report everyone at 100%.
        double top = ranking.ranked().stream()
                .mapToDouble(StackBranchScorer.Scored::score).max().orElse(0);

        List<ChoiceOptionResponse> options = new ArrayList<>(ranking.ranked().size());
        for (StackBranchScorer.Scored scored : ranking.ranked()) {
            SkillNode node = scored.node();
            DemandMatch demandMatch = resolveDisplayDemand(node, displayDemand);
            SkillDemandResponse demand = demandMatch.demand();
            boolean isChosen = node.getNodeId().equals(chosenNodeId);
            options.add(ChoiceOptionResponse.builder()
                    .nodeId(node.getNodeId())
                    .name(node.getNodeName())
                    .fitScore(top > 0 ? scored.score() / top : 0.0)
                    .fitReason(fitReasonFor(scored, ranking))
                    .matchedSkills(scored.contributions().stream()
                            .limit(MATCHED_SKILLS_SHOWN)
                            .map(c -> MatchedSkillResponse.builder()
                                    .skillName(c.skillName())
                                    .proficiency(c.proficiency())
                                    .verified(c.verified())
                                    .build())
                            .toList())
                    .marketFrequency(demand == null ? null : demand.getFrequency())
                    .marketJobCount(demand == null ? null : demand.getJobCount())
                    .skillId(demandMatch.skillId())
                    .nodeCount(node.getSubtreeSize())
                    .chosen(isChosen)
                    .autoSelected(isChosen && autoSelected)
                    .build());
        }

        return ChoiceOptionsResponse.builder()
                .groupNodeId(groupNodeId)
                .groupName(group.getNodeName())
                .verdict(ranking.verdict().name())
                .options(options)
                .build();
    }

    /**
     * Choice options are TOPIC nodes, so they intentionally carry no skill_id.
     * Market demand still belongs to a measurable catalog skill. Resolve that
     * boundary here instead of restoring the old topic -> skill coupling.
     */
    private DemandMatch resolveDisplayDemand(SkillNode node,
                                             Map<UUID, SkillDemandResponse> displayDemand) {
        if (node.getSkill() != null && node.getSkill().getSkillId() != null) {
            UUID skillId = node.getSkill().getSkillId();
            return new DemandMatch(skillId, displayDemand.get(skillId));
        }

        for (String candidate : marketSkillNames(node.getNodeName())) {
            var skill = skillRepository.findOneBySkillNameIgnoreCase(candidate);
            if (skill != null && displayDemand.containsKey(skill.getSkillId())) {
                return new DemandMatch(skill.getSkillId(), displayDemand.get(skill.getSkillId()));
            }
        }
        return DemandMatch.EMPTY;
    }

    private static List<String> marketSkillNames(String optionName) {
        if (optionName == null || optionName.isBlank()) {
            return List.of();
        }
        return switch (optionName.trim().toLowerCase(Locale.ROOT)) {
            case "golang" -> List.of("Go", "Golang");
            case "javascript (node.js)" -> List.of("Node.js", "JavaScript");
            case "nodejs" -> List.of("Node.js", "NodeJS");
            case ".net framework based" -> List.of("C#", ".NET");
            default -> List.of(optionName.trim());
        };
    }

    private record DemandMatch(UUID skillId, SkillDemandResponse demand) {
        private static final DemandMatch EMPTY = new DemandMatch(null, null);
    }

    /**
     * A sentence only for the branch the scorer would actually have picked.
     *
     * <p>Every option carries matched skills, and writing "you have X" under all
     * nine would read as nine recommendations. The reason is the claim; the
     * chips are the evidence, and only the claim is rationed.
     */
    private String fitReasonFor(StackBranchScorer.Scored scored, StackBranchScorer.Ranking ranking) {
        if (ranking.verdict() != StackBranchScorer.Verdict.DECISIVE
                || ranking.ranked().isEmpty()
                || !ranking.ranked().get(0).node().getNodeId().equals(scored.node().getNodeId())
                || scored.contributions().isEmpty()) {
            return null;
        }
        StackBranchScorer.Contribution best = scored.contributions().get(0);
        return "You already have " + best.skillName()
                + (best.verified() ? " (verified)" : "")
                + ", ahead of " + ranking.ranked().get(1).node().getNodeName() + ".";
    }

    /**
     * Runs in its own write transaction because the roadmap read path (a
     * read-only transaction) triggers it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public int autoDefaultSelections() {
        Student student = authenticatedStudentService.getRequiredStudent();
        if (student.getCareerRole() == null || student.getCareerRole().getCareerId() == null) {
            return 0;
        }

        List<SkillNode> careerNodes = skillNodeRepository
                .findByCareerRole_CareerId(student.getCareerRole().getCareerId());
        if (careerNodes.isEmpty()) {
            return 0;
        }

        Map<UUID, StudentNodeSelection> autoSelectionsByGroup = new HashMap<>();
        Set<UUID> manuallyChosenGroups = new HashSet<>();
        for (StudentNodeSelection selection : selectionRepository.findByStudent_UserId(student.getUserId())) {
            UUID groupId = selection.getGroupNode().getNodeId();
            if (Boolean.TRUE.equals(selection.getAutoSelected())) {
                autoSelectionsByGroup.put(groupId, selection);
            } else {
                manuallyChosenGroups.add(groupId);
            }
        }

        Set<String> skillNames = new HashSet<>();
        Map<UUID, StudentSkill> heldBySkillId = new HashMap<>();
        for (StudentSkill studentSkill : studentSkillRepository.findByStudent_UserId(student.getUserId())) {
            if (studentSkill.getSkill() != null && studentSkill.getSkill().getSkillName() != null) {
                skillNames.add(studentSkill.getSkill().getSkillName().toLowerCase(Locale.ROOT));
            }
            if (studentSkill.getSkill() != null && studentSkill.getSkill().getSkillId() != null) {
                heldBySkillId.put(studentSkill.getSkill().getSkillId(), studentSkill);
            }
        }
        if (skillNames.isEmpty()) {
            return 0;
        }

        // Market relevance for the scorer. A failure here costs the scoring pass,
        // not the auto-selection: the name matcher below still runs.
        Map<UUID, SkillDemandResponse> demandBySkill;
        try {
            demandBySkill = marketDemandService.demandBySkill(student.getCareerRole().getCareerId());
        } catch (Exception e) {
            log.warn("RoadmapSelectionServiceImpl: no market demand for career {}: {}",
                    student.getCareerRole().getCareerId(), e.getMessage());
            demandBySkill = Map.of();
        }
        Map<UUID, List<SkillNode>> childrenByParent = StackBranchScorer.indexByParent(careerNodes);

        // Children of each CHOOSE_ONE group that has no stored selection yet.
        Map<UUID, SkillNode> groupsById = new HashMap<>();
        Map<UUID, List<SkillNode>> alternativesByGroup = new HashMap<>();
        for (SkillNode node : careerNodes) {
            if (CHOOSE_ONE.equalsIgnoreCase(node.getSelection())
                    && !manuallyChosenGroups.contains(node.getNodeId())) {
                groupsById.put(node.getNodeId(), node);
            }
        }
        for (SkillNode node : careerNodes) {
            if (node.getParentNode() != null && groupsById.containsKey(node.getParentNode().getNodeId())) {
                alternativesByGroup
                        .computeIfAbsent(node.getParentNode().getNodeId(), key -> new ArrayList<>())
                        .add(node);
            }
        }

        int created = 0;
        for (Map.Entry<UUID, List<SkillNode>> entry : alternativesByGroup.entrySet()) {
            SkillNode group = groupsById.get(entry.getKey());
            SkillNode chosen = null;
            String reason = null;

            // Score the whole subtree first. This is what handles the branch a
            // name comparison cannot see: inside "Pick a Language" the node called
            // JavaScript is linked to the skill "Pick a Language" and carries no
            // children, while JavaScript (Node.js) is the one that actually holds
            // the JavaScript track. Names disagree with the data; the subtree does
            // not.
            StackBranchScorer.BranchVerdict verdict = stackBranchScorer.pick(
                    entry.getValue(), childrenByParent, heldBySkillId, demandBySkill);
            if (verdict != null) {
                chosen = verdict.chosen();
                reason = verdict.reason();
            } else {
                // Falls back to the original exact-name rule, which still answers
                // the case the scorer cannot: a student who declared the skill but
                // whose market relevance is unknown scores zero everywhere.
                List<SkillNode> matches = entry.getValue().stream()
                        .filter(alternative -> matchesStudentSkill(alternative, skillNames))
                        .toList();
                // Only act on an unambiguous signal; zero or multiple matches keep
                // the decision with the student.
                if (matches.size() != 1) {
                    continue;
                }
                chosen = matches.get(0);
                reason = "Chose " + chosen.getNodeName() + " because it is a skill you declared.";
            }

            StudentNodeSelection selection = autoSelectionsByGroup.get(group.getNodeId());
            boolean isNew = selection == null;
            if (isNew) {
                selection = StudentNodeSelection.builder()
                        .student(Student.builder().userId(student.getUserId()).build())
                        .groupNode(group)
                        .build();
            }
            selection.setChosenNode(chosen);
            selection.setAutoSelected(Boolean.TRUE);
            selection.setAutoReason(reason);
            selectionRepository.save(selection);
            if (isNew) {
                created++;
            }
            log.info("RoadmapSelectionServiceImpl: Auto-selected '{}' in group '{}' for student {} — {}",
                    chosen.getNodeName(), group.getNodeName(), student.getUserId(), reason);
        }
        return created;
    }

    /** True when the alternative's name or one of its evidence keywords is a profile skill. */
    private boolean matchesStudentSkill(SkillNode alternative, Set<String> skillNames) {
        if (alternative.getNodeName() != null
                && skillNames.contains(alternative.getNodeName().toLowerCase(Locale.ROOT))) {
            return true;
        }
        JsonNode keywords = alternative.getEvidenceKeywords();
        if (keywords != null && keywords.isArray()) {
            for (JsonNode keyword : keywords) {
                if (skillNames.contains(keyword.asText("").toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Loads the group node and validates it is a CHOOSE_ONE group belonging to
     * the student's selected career.
     */
    private SkillNode getChoiceGroupOwnedByStudentCareer(UUID groupNodeId, Student student) {
        SkillNode group = skillNodeRepository.findById(groupNodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Group node not found"));

        if (!CHOOSE_ONE.equalsIgnoreCase(group.getSelection())) {
            throw new BadRequestException("Node '" + group.getNodeName() + "' is not a choose-one group");
        }
        if (student.getCareerRole() == null || student.getCareerRole().getCareerId() == null) {
            throw new BadRequestException("Student has not selected a career path");
        }
        if (group.getCareerRole() == null
                || !student.getCareerRole().getCareerId().equals(group.getCareerRole().getCareerId())) {
            throw new BadRequestException("Group node does not belong to the student's career roadmap");
        }
        return group;
    }

    private NodeSelectionResponse toResponse(StudentNodeSelection selection, SkillNode group, SkillNode chosen) {
        return NodeSelectionResponse.builder()
                .groupNodeId(group.getNodeId())
                .groupNodeName(group.getNodeName())
                .chosenNodeId(chosen.getNodeId())
                .chosenNodeName(chosen.getNodeName())
                .createdAt(selection.getCreatedAt())
                .autoSelected(selection.getAutoSelected())
                .autoReason(selection.getAutoReason())
                .build();
    }
}
