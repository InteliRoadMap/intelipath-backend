package com.inteliroadmap.backend.components;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Which career a job posting is for, read from its title.
 *
 * <p>Deterministic keyword rules, not a model. The classification decides which
 * skills count as demand for which career, so it has to be inspectable, repeatable,
 * and re-runnable when the rules improve — none of which a per-posting LLM call
 * would give, at any price.
 *
 * <p><b>Refusing to answer is a supported outcome.</b> A posting titled
 * "Software Engineer" or "IT Staff" names no specialisation, and forcing it into the
 * nearest career would make it evidence that that role requires whatever the posting
 * happens to mention. Counting a skill for a career that never asked for it is worse
 * than counting nothing: the first corrupts the answer, the second only narrows the
 * sample. Measured on the current 913 postings, the rules resolve 494 and decline 419.
 *
 * <p>Order matters and is deliberate. "Fullstack Java Developer" must be Full Stack,
 * not Backend, so the compound specialisations are tested before the single-stack
 * keywords that would also match them.
 */
@Component
@Slf4j
public class RecruitmentCareerClassifier {

    /**
     * A career and the titles that mean it.
     *
     * <p>Patterns are matched against a lower-cased title. {@code \b} boundaries keep
     * "ai" from matching "maintain" and "qa" from matching "qatar".
     */
    private record Rule(String careerName, Pattern pattern) {}

    /**
     * Tested top to bottom; first match wins.
     *
     * <p>Full Stack sits first because its titles contain the words that would
     * otherwise class them as Backend or Frontend. Architect sits next because
     * "Solution Architect (Java)" is an architect job, not a Java one — seniority and
     * role outrank the technology named in brackets.
     */
    private static final List<Rule> RULES = List.of(
            new Rule("Full Stack", compile("full[ -]?stack")),
            new Rule("Software Architect", compile("\\barchitect\\b|technical lead|tech lead")),
            new Rule("QA", compile("\\bqa\\b|\\bqc\\b|tester|test engineer|quality assurance"
                    + "|automation test|manual test")),
            new Rule("DevOps", compile("devops|\\bsre\\b|site reliability|infrastructure engineer"
                    + "|cloud engineer|platform engineer|system admin|sysadmin")),
            new Rule("Data Science", compile("data scien|data engineer|data analy|machine learning"
                    + "|\\bml engineer\\b|\\bai engineer\\b|\\bnlp\\b|big data|business intelligence")),
            new Rule("Game Developer", compile("game (developer|dev|programmer)|\\bunity\\b|unreal")),
            new Rule("Frontend", compile("front[ -]?end|\\breact(js| developer|\\.js)|\\bangular\\b"
                    + "|\\bvue(js|\\.js)?\\b|\\bui developer\\b")),
            new Rule("Backend", compile("back[ -]?end|\\bjava\\b(?!script)|\\.net\\b|\\bc#|spring boot"
                    + "|\\bnode(js|\\.js)\\b|\\bgolang\\b|\\bphp\\b|\\bpython developer\\b"
                    + "|\\bruby\\b|\\bapi developer\\b"))
    );

    private static Pattern compile(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    /**
     * @param title the posting's title, as scraped
     * @return the career name, or {@code null} when the title names no specialisation.
     *         Null is a real answer and callers must keep it — see the class note.
     */
    public String classify(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        String normalised = title.toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(normalised).find()) {
                return rule.careerName();
            }
        }
        return null;
    }
}
