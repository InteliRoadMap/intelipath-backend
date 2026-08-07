package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.dto.response.market.SkillDemandResponse;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadmapEdgeResolverTest {

    private RoadmapEdgeResolver resolver;
    private SelectionView nothingExcluded;

    private Skill react;
    private Skill vue;
    private SkillNode frameworks;   // topic
    private SkillNode reactNode;
    private SkillNode vueNode;

    @BeforeEach
    void setUp() {
        resolver = new RoadmapEdgeResolver(new NodeSkillMatcher());
        nothingExcluded = new SelectionView(Set.of(), Set.of(), Set.of());

        react = skill("React");
        vue = skill("Vue");
        frameworks = node("Frameworks", 1, null, null);
        reactNode = node("React", 0, frameworks, react);
        vueNode = node("Vue", 0, frameworks, vue);
    }

    /**
     * The regression property the whole change rests on: a student we know
     * nothing about must receive today's roadmap, node for node.
     */
    @Test
    void emptyContextReproducesTheStaticOrder() {
        List<SkillNode> nodes = List.of(frameworks, vueNode, reactNode,
                node("Angular", 0, frameworks, skill("Angular")));

        ResolvedOrder order = resolver.resolve(nodes, StudentRoadmapContext.empty(), nothingExcluded);

        // The database order is node_level then node_name; children only ever
        // appear inside their own topic, so compare within the sibling group.
        List<String> staticOrder = nodes.stream()
                .filter(n -> n.getParentNode() != null)
                .sorted(Comparator.comparing(SkillNode::getNodeName))
                .map(SkillNode::getNodeName)
                .toList();

        assertEquals(staticOrder, namesOf(order.visitOrder(), nodes).stream()
                .filter(name -> !name.equals("Frameworks"))
                .toList());
    }

    @Test
    void marketDemandPromotesTheMoreWantedSkill() {
        List<SkillNode> nodes = List.of(frameworks, reactNode, vueNode);

        // Alphabetically React already precedes Vue, so prove the ordering is
        // demand-driven by making the alphabetically LATER skill the wanted one.
        StudentRoadmapContext vueWanted = new StudentRoadmapContext(
                Map.of(), Set.of(), null,
                Map.of(vue.getSkillId(), demand(0.41), react.getSkillId(), demand(0.06)),
                Map.of());

        ResolvedOrder order = resolver.resolve(nodes, vueWanted, nothingExcluded);

        assertEquals(vueNode.getNodeId(), order.previousByNodeId().get(reactNode.getNodeId()));
        assertNotEquals(
                resolver.resolve(nodes, StudentRoadmapContext.empty(), nothingExcluded).previousByNodeId(),
                order.previousByNodeId());
    }

    @Test
    void heldSkillsMoveToTheFrontButStayInTheGraph() {
        List<SkillNode> nodes = List.of(frameworks, reactNode, vueNode);
        StudentRoadmapContext knowsVue = new StudentRoadmapContext(
                Map.of(vue.getSkillId(), (short) 3), Set.of("vue"), null, Map.of(), Map.of());

        ResolvedOrder order = resolver.resolve(nodes, knowsVue, nothingExcluded);

        assertEquals(vueNode.getNodeId(), order.previousByNodeId().get(reactNode.getNodeId()));
        assertTrue(order.visitOrder().contains(vueNode.getNodeId()),
                "a skill the student already has must stay in the graph, or progress % lies");
        assertEquals(nodes.size(), order.visitOrder().size());
    }

    /**
     * Missing market data must not read as "the market does not want this" — the
     * measured skill and the unmeasured one stay in their alphabetical order.
     */
    @Test
    void missingDemandDoesNotSinkANode() {
        List<SkillNode> nodes = List.of(frameworks, reactNode, vueNode);
        StudentRoadmapContext onlyReactMeasured = new StudentRoadmapContext(
                Map.of(), Set.of(), null, Map.of(react.getSkillId(), demand(0.5)), Map.of());

        ResolvedOrder order = resolver.resolve(nodes, onlyReactMeasured, nothingExcluded);

        assertEquals(reactNode.getNodeId(), order.previousByNodeId().get(vueNode.getNodeId()));
    }

    @Test
    void nodesWithoutASkillStillGetOrderedAndExplained() {
        SkillNode orphanA = node("Zebra topic", 0, frameworks, null);
        SkillNode orphanB = node("Alpha topic", 0, frameworks, null);
        List<SkillNode> nodes = List.of(frameworks, orphanA, orphanB);

        ResolvedOrder order = resolver.resolve(nodes, StudentRoadmapContext.empty(), nothingExcluded);

        assertEquals(orphanB.getNodeId(), order.previousByNodeId().get(orphanA.getNodeId()));
        assertFalse(order.edges().isEmpty());
        order.edges().forEach(edge -> assertFalse(edge.reason() == null || edge.reason().isBlank(),
                "every edge must be able to say why it exists"));
    }

    /** An unchosen alternative must neither gate nor be gated. */
    @Test
    void offPathAlternativesAreSkippedInTheChain() {
        List<SkillNode> nodes = List.of(frameworks, reactNode, vueNode,
                node("Angular", 0, frameworks, skill("Angular")));
        SelectionView vueExcluded = new SelectionView(Set.of(vueNode.getNodeId()), Set.of(), Set.of());

        ResolvedOrder order = resolver.resolve(nodes, StudentRoadmapContext.empty(), vueExcluded);

        assertFalse(order.previousByNodeId().containsKey(vueNode.getNodeId()));
        assertFalse(order.previousByNodeId().containsValue(vueNode.getNodeId()));
        assertTrue(order.visitOrder().contains(vueNode.getNodeId()));
    }

    @Test
    void everyVisibleNodeAppearsExactlyOnceInVisitOrder() {
        List<SkillNode> nodes = List.of(frameworks, reactNode, vueNode,
                node("Testing", 1, null, null));

        ResolvedOrder order = resolver.resolve(nodes, StudentRoadmapContext.empty(), nothingExcluded);

        assertEquals(nodes.size(), order.visitOrder().size());
        assertEquals(nodes.size(), Set.copyOf(order.visitOrder()).size());
    }

    /** A parent must always be visited before its children, or gating breaks. */
    @Test
    void parentsPrecedeTheirChildren() {
        List<SkillNode> nodes = List.of(reactNode, vueNode, frameworks);

        ResolvedOrder order = resolver.resolve(nodes,
                new StudentRoadmapContext(Map.of(), Set.of(), null, Map.of(),
                        Map.of(react.getSkillId(), ImportanceLevel.HIGH)),
                nothingExcluded);

        assertTrue(order.visitOrder().indexOf(frameworks.getNodeId())
                < order.visitOrder().indexOf(reactNode.getNodeId()));
    }

    /**
     * The trap this whole change had to survive: the status pass reads the status
     * of each node's predecessor, and an unknown status counts as locked. So every
     * predecessor must appear earlier in {@code visitOrder} than the node it gates
     * — including when the profile order runs opposite to the database order,
     * which is precisely when walking the nodes in database order would fail.
     */
    @Test
    void everyPredecessorIsVisitedBeforeTheNodeItGates() {
        SkillNode alpha = node("Alpha", 0, frameworks, skill("Alpha"));
        SkillNode beta = node("Beta", 0, frameworks, skill("Beta"));
        SkillNode gamma = node("Gamma", 0, frameworks, skill("Gamma"));
        List<SkillNode> nodes = List.of(frameworks, alpha, beta, gamma);

        // Demand exactly reverses the alphabetical order the database returns.
        StudentRoadmapContext reversing = new StudentRoadmapContext(
                Map.of(), Set.of(), null,
                Map.of(gamma.getSkill().getSkillId(), demand(0.9),
                        beta.getSkill().getSkillId(), demand(0.5),
                        alpha.getSkill().getSkillId(), demand(0.1)),
                Map.of());

        ResolvedOrder order = resolver.resolve(nodes, reversing, nothingExcluded);

        assertEquals(List.of("Frameworks", "Gamma", "Beta", "Alpha"),
                namesOf(order.visitOrder(), nodes));
        order.previousByNodeId().forEach((nodeId, previousId) ->
                assertTrue(order.visitOrder().indexOf(previousId) < order.visitOrder().indexOf(nodeId),
                        "a predecessor visited after its dependant would lock the rest of the roadmap"));
    }

    /**
     * The spine encodes the teaching progression in {@code node_level}: Internet 1,
     * HTML 2 … Authentication 13. No amount of market demand may cross it, or a
     * beginner is told to start at Authentication with HTML locked behind it.
     */
    @Test
    void marketDemandNeverReordersAcrossNodeLevels() {
        SkillNode html = node("HTML", 2, null, skill("HTML"));
        SkillNode authentication = node("Authentication", 13, null, skill("Authentication"));
        List<SkillNode> nodes = List.of(html, authentication);

        StudentRoadmapContext authenticationInDemand = new StudentRoadmapContext(
                Map.of(), Set.of(), null,
                Map.of(authentication.getSkill().getSkillId(), demand(0.95),
                        html.getSkill().getSkillId(), demand(0.05)),
                Map.of(authentication.getSkill().getSkillId(), ImportanceLevel.HIGH,
                        html.getSkill().getSkillId(), ImportanceLevel.LOW));

        ResolvedOrder order = resolver.resolve(nodes, authenticationInDemand, nothingExcluded);

        assertEquals(List.of("HTML", "Authentication"), namesOf(order.visitOrder(), nodes));
        assertEquals(html.getNodeId(), order.previousByNodeId().get(authentication.getNodeId()));
    }

    /** Within one level the profile is free to reorder — that is where the 896 leaves live. */
    @Test
    void reorderingStillHappensWithinALevel() {
        List<SkillNode> nodes = List.of(frameworks, reactNode, vueNode);
        StudentRoadmapContext vueWanted = new StudentRoadmapContext(
                Map.of(), Set.of(), null,
                Map.of(vue.getSkillId(), demand(0.41), react.getSkillId(), demand(0.06)),
                Map.of());

        ResolvedOrder order = resolver.resolve(nodes, vueWanted, nothingExcluded);

        assertEquals(vueNode.getNodeId(), order.previousByNodeId().get(reactNode.getNodeId()));
    }

    /**
     * The point of TF-IDF: "Agile" appears in more postings than "Spring Boot"
     * and still belongs further down a backend roadmap, because frequency
     * measures how common a term is and relevance measures how much it belongs
     * to this career.
     */
    @Test
    void relevanceOutranksBareFrequency() {
        StudentRoadmapContext ctx = new StudentRoadmapContext(
                Map.of(), Set.of(), null,
                Map.of(react.getSkillId(), demand(0.05, 0.30),   // rare, but characteristic
                       vue.getSkillId(), demand(0.25, 0.02)),    // everywhere, says nothing
                Map.of());

        ResolvedOrder order = resolver.resolve(List.of(frameworks, vueNode, reactNode), ctx, nothingExcluded);

        assertEquals(reactNode.getNodeId(), order.visitOrder().get(1),
                "the characteristic skill leads even though it is mentioned less often");
    }

    @Test
    void priorityLabelsSitOnTheirBands() {
        assertEquals(RoadmapEdgeResolver.PriorityLabel.NORMAL,
                resolver.priorityOf(reactNode, StudentRoadmapContext.empty()).label(),
                "no signal at all scores 0.5 on every term and must not read as urgent");

        // HIGH importance, top of the career's demand, dead-on level fit.
        StudentRoadmapContext hot = new StudentRoadmapContext(
                Map.of(), Set.of(), null,
                Map.of(react.getSkillId(), demand(0.19, 0.30)),
                Map.of(react.getSkillId(), ImportanceLevel.HIGH));
        RoadmapEdgeResolver.NodePriority priority = resolver.priorityOf(reactNode, hot);

        assertEquals(RoadmapEdgeResolver.PriorityLabel.CRITICAL, priority.label());
        assertTrue(priority.score() >= 0.75, "score and label must agree: " + priority.score());
    }

    /** Every clause has to trace back to a number, or it is decoration. */
    @Test
    void priorityReasonNamesOnlyTermsThatHaveASignal() {
        StudentRoadmapContext ctx = new StudentRoadmapContext(
                Map.of(), Set.of(), null,
                Map.of(react.getSkillId(), demand(0.19, 0.30)),
                Map.of(react.getSkillId(), ImportanceLevel.HIGH));

        String reason = resolver.priorityOf(reactNode, ctx).reason();
        assertTrue(reason.contains("essential for this career"), reason);
        assertTrue(reason.contains("19% of recent postings ask for it"), reason);
        assertTrue(reason.contains("you do not have it yet"), reason);

        // A node the system knows nothing about says nothing rather than claiming
        // the market wants it.
        assertNull(resolver.priorityOf(vueNode, StudentRoadmapContext.empty()).reason());
    }

    private List<String> namesOf(List<UUID> ids, List<SkillNode> nodes) {
        return ids.stream()
                .map(id -> nodes.stream().filter(n -> n.getNodeId().equals(id)).findFirst().orElseThrow())
                .map(SkillNode::getNodeName)
                .toList();
    }

    /**
     * A demand row in the shape the market service actually produces.
     *
     * <p>Relevance is what the resolver ranks on, so a fixture carrying only
     * {@code frequency} models a response that no longer exists — every row
     * MarketDemandServiceImpl emits has both. Here they move together, which is
     * the ordinary case; {@link #relevanceOutranksBareFrequency()} covers the
     * case where they disagree, which is the whole reason TF-IDF replaced the
     * raw percentage.
     */
    private SkillDemandResponse demand(double frequency) {
        return demand(frequency, frequency);
    }

    private SkillDemandResponse demand(double frequency, double relevance) {
        return SkillDemandResponse.builder().frequency(frequency).relevance(relevance).build();
    }

    private Skill skill(String name) {
        Skill skill = new Skill();
        skill.setSkillId(UUID.randomUUID());
        skill.setSkillName(name);
        return skill;
    }

    private SkillNode node(String name, Integer level, SkillNode parent, Skill skill) {
        return SkillNode.builder()
                .nodeId(UUID.randomUUID())
                .nodeName(name)
                .nodeLevel(level)
                .parentNode(parent)
                .skill(skill)
                .build();
    }
}
