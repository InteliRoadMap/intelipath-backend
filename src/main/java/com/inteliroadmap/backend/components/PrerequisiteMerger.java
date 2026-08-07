package com.inteliroadmap.backend.components;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decides which prerequisite claims are allowed to stand.
 *
 * <p>Prerequisites arrive from two places. The roadmap authors supplied 2.040 of
 * them through {@code previous_id} — those are decisions someone made, and they
 * are treated as fact. The rest, the ordering <em>between</em> roadmaps ("Java
 * before Spring Boot", "Docker before Kubernetes"), has no source and must be
 * generated.
 *
 * <p>Generated claims get no human gate. A student's roadmap must never wait on
 * an approval queue — needing permission to learn is the wrong shape for the
 * product. Quality comes instead from three rules a machine can check, and that
 * can be shown to anyone who asks on what authority the ordering stands:
 *
 * <ol>
 *   <li><b>Source wins.</b> Where a generated claim contradicts an author's, the
 *       author's survives and the generated one is dropped.</li>
 *   <li><b>Low confidence is discarded, not queued.</b> A claim the generator is
 *       unsure of buys nothing and risks sending a student down the wrong order,
 *       so it simply does not exist.</li>
 *   <li><b>No cycles.</b> "A before B before A" would lock both nodes forever.
 *       Any generated claim that closes a cycle is rejected; source claims never
 *       are, because a cycle among them is a data bug to be reported, not
 *       silently patched.</li>
 * </ol>
 */
@Component
@Slf4j
public class PrerequisiteMerger {

    public static final String SOURCE_AUTHOR = "SOURCE";
    public static final String SOURCE_AI = "AI";

    /**
     * Confidence a generated claim must reach to be recorded.
     *
     * <p>0.70 rather than something permissive: an ordering claim is acted on
     * silently — the student never sees it, they only experience its
     * consequences — so an uncertain one is worse than none at all.
     */
    public static final BigDecimal MIN_AI_CONFIDENCE = new BigDecimal("0.70");

    /**
     * One "learn X before Y" claim.
     *
     * @param before the prerequisite
     * @param after  the node it unblocks
     * @param source {@link #SOURCE_AUTHOR} or {@link #SOURCE_AI}
     * @param reason one sentence, kept so the ordering can always be questioned
     */
    public record Claim(UUID before, UUID after, String source, BigDecimal confidence, String reason) {

        public boolean fromAuthor() {
            return SOURCE_AUTHOR.equals(source);
        }
    }

    /**
     * @param accepted claims that survived, in the order they were accepted
     * @param rejected claims that did not, each with the rule that rejected it
     */
    public record MergeResult(List<Claim> accepted, Map<Claim, String> rejected) {

        public int acceptedFromAuthor() {
            return (int) accepted.stream().filter(Claim::fromAuthor).count();
        }
    }

    /**
     * @param authored claims taken from the source roadmaps; trusted as given
     * @param generated claims produced by a model; each must earn its place
     */
    public MergeResult merge(List<Claim> authored, List<Claim> generated) {
        Map<UUID, Set<UUID>> beforeByAfter = new HashMap<>();
        List<Claim> accepted = new ArrayList<>();
        Map<Claim, String> rejected = new LinkedHashMap<>();

        // Authored first and unconditionally, so a later generated claim is always
        // the one that loses a conflict.
        for (Claim claim : authored) {
            if (claim.before() == null || claim.after() == null || claim.before().equals(claim.after())) {
                rejected.put(claim, "A node cannot be its own prerequisite.");
                continue;
            }
            beforeByAfter.computeIfAbsent(claim.after(), k -> new HashSet<>()).add(claim.before());
            accepted.add(claim);
        }

        for (Claim claim : generated) {
            String refusal = refuse(claim, beforeByAfter);
            if (refusal != null) {
                rejected.put(claim, refusal);
                continue;
            }
            beforeByAfter.computeIfAbsent(claim.after(), k -> new HashSet<>()).add(claim.before());
            accepted.add(claim);
        }

        log.info("PrerequisiteMerger: accepted {} claims ({} authored), rejected {}.",
                accepted.size(), (int) accepted.stream().filter(Claim::fromAuthor).count(), rejected.size());
        return new MergeResult(List.copyOf(accepted), Map.copyOf(rejected));
    }

    /** @return the rule that refuses this generated claim, or null to accept it. */
    private String refuse(Claim claim, Map<UUID, Set<UUID>> beforeByAfter) {
        if (claim.before() == null || claim.after() == null) {
            return "A claim must name both ends.";
        }
        if (claim.before().equals(claim.after())) {
            return "A node cannot be its own prerequisite.";
        }
        if (claim.confidence() == null || claim.confidence().compareTo(MIN_AI_CONFIDENCE) < 0) {
            return "Confidence " + claim.confidence() + " is below the " + MIN_AI_CONFIDENCE
                    + " a silent ordering decision has to clear.";
        }
        if (claim.reason() == null || claim.reason().isBlank()) {
            return "An ordering nobody can question is not one we record.";
        }
        // The reverse already holding means an author, or an earlier accepted
        // claim, said the opposite.
        if (beforeByAfter.getOrDefault(claim.before(), Set.of()).contains(claim.after())) {
            return "The opposite order is already established.";
        }
        if (reaches(claim.after(), claim.before(), beforeByAfter)) {
            return "Accepting this would close a cycle, locking both nodes forever.";
        }
        return null;
    }

    /**
     * Whether {@code from} is already a prerequisite of {@code target}, directly
     * or through a chain — the test for whether one more edge closes a loop.
     */
    private boolean reaches(UUID from, UUID target, Map<UUID, Set<UUID>> beforeByAfter) {
        Deque<UUID> pending = new ArrayDeque<>();
        Set<UUID> seen = new HashSet<>();
        pending.push(target);
        while (!pending.isEmpty()) {
            UUID current = pending.pop();
            if (!seen.add(current)) {
                continue;
            }
            if (current.equals(from)) {
                return true;
            }
            pending.addAll(beforeByAfter.getOrDefault(current, Set.of()));
        }
        return false;
    }
}
