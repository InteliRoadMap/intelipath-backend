package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.repositories.StudentNodeSelectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class RoadmapSelectionResolverTest {

    @Mock
    private StudentNodeSelectionRepository selectionRepository;

    private RoadmapSelectionResolver resolver;

    private SkillNode group;      // CHOOSE_ONE "Pick a Language"
    private SkillNode java;       // chosen alternative
    private SkillNode csharp;     // unchosen alternative
    private SkillNode csharpChild; // descendant of the unchosen alternative
    private SkillNode internet;   // an unrelated ALL topic
    private SkillNode http;       // child of the ALL topic

    @BeforeEach
    void setUp() {
        resolver = new RoadmapSelectionResolver(selectionRepository);

        group = node("Pick a Language", "CHOOSE_ONE", null);
        java = node("Java", "ALL", group);
        csharp = node("C#", "ALL", group);
        csharpChild = node("ASP.NET", "ALL", csharp);
        internet = node("Internet", "ALL", null);
        http = node("HTTP", "ALL", internet);
    }

    private SkillNode node(String name, String selection, SkillNode parent) {
        return SkillNode.builder()
                .nodeId(UUID.randomUUID())
                .nodeName(name)
                .selection(selection)
                .parentNode(parent)
                .build();
    }

    private List<SkillNode> allNodes() {
        return List.of(group, java, csharp, csharpChild, internet, http);
    }

    @Test
    void decidedGroup_greysAndExcludesUnchosenSubtree() {
        Map<UUID, UUID> chosen = Map.of(group.getNodeId(), java.getNodeId());

        SelectionView view = resolver.resolve(allNodes(), chosen);

        // Chosen alternative stays on the path.
        assertFalse(view.isExcludedFromProgress(java.getNodeId()));
        assertFalse(view.isGreyedAlternative(java.getNodeId()));

        // Unchosen alternative and its whole subtree are greyed + off the progress path.
        assertTrue(view.isGreyedAlternative(csharp.getNodeId()));
        assertTrue(view.isGreyedAlternative(csharpChild.getNodeId()));
        assertTrue(view.isExcludedFromProgress(csharp.getNodeId()));
        assertTrue(view.isExcludedFromProgress(csharpChild.getNodeId()));

        // The group header and unrelated ALL topics are untouched.
        assertFalse(view.isExcludedFromProgress(group.getNodeId()));
        assertFalse(view.isExcludedFromProgress(internet.getNodeId()));
        assertFalse(view.isExcludedFromProgress(http.getNodeId()));
    }

    @Test
    void undecidedGroup_excludesAllAlternativesFromProgressButGreysNone() {
        SelectionView view = resolver.resolve(allNodes(), Map.of());

        // No choice yet: every alternative is off the progress denominator...
        assertTrue(view.isExcludedFromProgress(java.getNodeId()));
        assertTrue(view.isExcludedFromProgress(csharp.getNodeId()));
        assertTrue(view.isExcludedFromProgress(csharpChild.getNodeId()));

        // ...but none are greyed out — the UI still offers them for picking.
        assertFalse(view.isGreyedAlternative(java.getNodeId()));
        assertFalse(view.isGreyedAlternative(csharp.getNodeId()));
    }

    @Test
    void activePathNodes_dropsExcludedNodes() {
        Map<UUID, UUID> chosen = Map.of(group.getNodeId(), java.getNodeId());
        SelectionView view = resolver.resolve(allNodes(), chosen);

        List<SkillNode> active = view.activePathNodes(allNodes());

        assertTrue(active.contains(java));
        assertTrue(active.contains(group));
        assertTrue(active.contains(http));
        assertFalse(active.contains(csharp));
        assertFalse(active.contains(csharpChild));
    }

    @Test
    void nonChooseOneChildren_areNeverExcluded() {
        SelectionView view = resolver.resolve(allNodes(), Map.of());

        // HTTP under an ALL topic is always on the path regardless of selections.
        assertFalse(view.isExcludedFromProgress(http.getNodeId()));
        assertFalse(view.isGreyedAlternative(http.getNodeId()));
    }

    /**
     * The point of picking a language: what hangs below the ones you did not pick
     * stops being sent at all. Greying them still shipped every language's whole
     * subtree to a page that then had to draw it.
     */
    @Test
    void decidedGroup_dropsDescendantsOfUnchosenAlternatives() {
        SelectionView view = resolver.resolve(
                allNodes(), Map.of(group.getNodeId(), java.getNodeId()));

        assertTrue(view.isOffPathDescendant(csharpChild.getNodeId()));
        // The alternative itself survives, so the student can see what they turned
        // down and change their mind. Cutting it would make the choice invisible.
        assertFalse(view.isOffPathDescendant(csharp.getNodeId()));
        assertFalse(view.isOffPathDescendant(java.getNodeId()));
        assertFalse(view.isOffPathDescendant(http.getNodeId()));
    }

    /** Nothing may be cut while the student is still choosing. */
    @Test
    void undecidedGroup_keepsEveryAlternativeWhole() {
        SelectionView view = resolver.resolve(allNodes(), Map.of());

        assertTrue(view.offPathDescendants().isEmpty());
    }
}
