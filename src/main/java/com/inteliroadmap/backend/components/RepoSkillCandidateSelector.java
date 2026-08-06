package com.inteliroadmap.backend.components;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Picks which of a career's skills are worth putting in front of the model for one
 * particular repository.
 *
 * <p>The catalog cannot be sent whole. Handing the model all 1,466 skills for a career
 * was measured returning zero matches — too much hay, no needle. Truncating it to 200
 * fixed that and introduced a quieter fault: past the sixtieth skill nothing has any
 * market demand behind it, every remaining row ties at zero postings, and the query's
 * final tie-break is the skill's <em>name</em>. The list therefore ran alphabetically.
 *
 * <p>Measured on the Backend career: 60 of the 200 skills sent had market data; the other
 * 140 were simply the alphabetically earliest of the remaining 1,374 — {@code Arithmetic},
 * {@code awk}, {@code Basic Syntax}, {@code Comments}. Meanwhile Maven sat at rank 881,
 * JWT at 796 and Hibernate at 729, so a 1.1 MB Spring Boot repository could not have its
 * build tool or its auth recognised: those names were never shown to the model. It
 * returned three skills, and that was the most it could return.
 *
 * <p>So the shortlist is chosen for the repository instead of for the alphabet. Whatever
 * the repository itself says — its languages, its build file, its README, the student's
 * own commit subjects — decides which names are worth the space, and only the leftover
 * slots go to market demand. The prompt is exactly the same size; it just stops spending
 * three-quarters of itself on words beginning with A.
 */
@Component
@Slf4j
public class RepoSkillCandidateSelector {

    /**
     * Names shorter than this are not matched against the repository text.
     *
     * <p>Substring matching on a two-character skill is worthless: {@code Go} appears
     * inside "Google", "going" and "algorithm", and {@code C} inside everything. Short
     * names still reach the model through the market-demand fill below, which does not
     * depend on finding them in the text.
     */
    private static final int MIN_MATCHABLE_LENGTH = 3;

    /**
     * @param careerSkillsByDemand every skill of the career, already ordered by market
     *                             demand — the fallback ordering for the leftover slots
     * @param repoSignals          text the repository produced about itself: language
     *                             names, build file, README, commit subjects
     * @param languageBytes        GitHub's own measurement, used to put the languages
     *                             actually written at the front of the shortlist
     * @param limit                how many names the prompt has room for
     */
    public List<String> select(List<String> careerSkillsByDemand, List<String> repoSignals,
                               Map<String, Long> languageBytes, int limit) {
        if (careerSkillsByDemand == null || careerSkillsByDemand.isEmpty() || limit <= 0) {
            return List.of();
        }

        String haystack = buildHaystack(repoSignals, languageBytes);

        // LinkedHashSet: the order is the priority, and a skill must not occupy two slots.
        Set<String> chosen = new LinkedHashSet<>();

        if (!haystack.isBlank()) {
            for (String skill : careerSkillsByDemand) {
                if (chosen.size() >= limit) {
                    break;
                }
                if (mentionedIn(haystack, skill)) {
                    chosen.add(skill);
                }
            }
        }
        int fromRepo = chosen.size();

        // Whatever room is left goes to the most in-demand skills, so a repository that
        // says little about itself is no worse off than it was before.
        for (String skill : careerSkillsByDemand) {
            if (chosen.size() >= limit) {
                break;
            }
            chosen.add(skill);
        }

        List<String> candidates = new ArrayList<>(chosen);
        log.info("RepoSkillCandidateSelector: {} candidate(s) — {} matched the repository, "
                        + "{} filled by market demand (from a career catalog of {})",
                candidates.size(), fromRepo, candidates.size() - fromRepo, careerSkillsByDemand.size());
        return candidates;
    }

    /** Everything the repository says about itself, lower-cased into one searchable blob. */
    private String buildHaystack(List<String> repoSignals, Map<String, Long> languageBytes) {
        StringBuilder text = new StringBuilder();
        if (languageBytes != null) {
            languageBytes.keySet().forEach(language -> text.append(language).append('\n'));
        }
        if (repoSignals != null) {
            repoSignals.stream()
                    .filter(signal -> signal != null && !signal.isBlank())
                    .forEach(signal -> text.append(signal).append('\n'));
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Whether the repository text mentions this skill.
     *
     * <p>Bounded on both sides so {@code Java} does not match "JavaScript" and {@code Go}
     * does not match "Google" — a shortlist built by accident is worse than a short one,
     * because every wrong name pushes out a right one and the model is being told these
     * are the relevant skills.
     */
    private boolean mentionedIn(String haystack, String skillName) {
        if (skillName == null || skillName.length() < MIN_MATCHABLE_LENGTH) {
            return false;
        }
        String needle = skillName.toLowerCase(Locale.ROOT);
        int from = 0;
        while (true) {
            int at = haystack.indexOf(needle, from);
            if (at < 0) {
                return false;
            }
            boolean leftClear = at == 0 || !isWordChar(haystack.charAt(at - 1));
            int end = at + needle.length();
            boolean rightClear = end >= haystack.length() || !isWordChar(haystack.charAt(end));
            if (leftClear && rightClear) {
                return true;
            }
            from = at + 1;
        }
    }

    /** Letters and digits only: '.', '-', '+' and '#' are part of names like Node.js and C#. */
    private boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c);
    }
}
