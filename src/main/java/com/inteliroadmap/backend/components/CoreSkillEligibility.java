package com.inteliroadmap.backend.components;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides whether a name is specific enough to be a student's <em>core</em> skill.
 *
 * <h2>The question this answers</h2>
 *
 * <p>{@code SeniorityCalculator.CORE_GRADES} reads HIGH rows and nothing else, so the HIGH
 * set is simultaneously the readiness denominator, the Skill Map's axis population, and
 * the list a roadmap tells a student to go and learn. A row that cannot be measured
 * against anything therefore does not merely sit there quietly — it divides every
 * student's readiness by a slightly larger number, forever.
 *
 * <p>Measured on the live database: 45 HIGH rows had zero postings behind them, and the
 * distribution gives the fault away — <b>eight per career</b>, the fingerprint of a
 * hand-written seed rather than anything derived. Frontend carried 26 HIGH rows of which
 * 17 were unmeasurable, so a Frontend student's readiness was divided by 26 when only 9
 * of those rows named something an employer had ever asked for.
 *
 * <h2>Why rules and not a list of 45</h2>
 *
 * <p>A hand-written exclusion list would fix today and be wrong again after the next seed,
 * for the same reason {@code CareerSkillMarketGrader} is code rather than a migration.
 * Reading the 45 rows, they fall into three shapes, and each shape is decidable from the
 * name alone:
 *
 * <ol>
 *   <li><b>Compound.</b> {@code Cloud Computing & AWS}, {@code Java / Kotlin / Scala /
 *       Swift}, {@code Testing Methodologies & Techniques}. These name two or more things.
 *       "Has the student got it?" has no answer, because there is no single "it".
 *   <li><b>Heading suffix.</b> {@code Software Architecture Fundamentals}, {@code QA
 *       Fundamentals}, {@code Architecture Styles}, {@code Design Patterns}. These are
 *       chapter titles from a curriculum outline. The student is measured on the contents,
 *       not the title.
 *   <li><b>Category word.</b> {@code Cloud}, {@code API}, {@code Database},
 *       {@code Software Development}. These came from the model, not the seed — measured
 *       this run, the LLM added {@code Cloud} to 58 postings and {@code API} to 49 while
 *       adding nothing at all to Python, Java or Docker. A posting that says "AWS" is
 *       evidence about AWS; recording it as "Cloud" throws away the only part worth
 *       teaching.
 * </ol>
 *
 * <h2>What this does not do</h2>
 *
 * <p><b>It never deletes.</b> A failing row stays in {@code career_required_skills} at AVG.
 * The catalog is also the roadmap's curriculum, and {@code Web Security and OWASP} is a
 * perfectly good thing to teach — it is just not a thing to divide a readiness percentage
 * by. This is the same line {@code CareerSkillDemandDeriver} already draws: <i>absence of
 * postings is absence of evidence</i>.
 *
 * <p><b>It does not judge market demand.</b> {@code OpenGL}, {@code Vulkan} and
 * {@code JMeter} have zero Vietnamese postings and pass every rule here, because they are
 * real, single, specific skills. Whether the market wants them is
 * {@code CareerSkillMarketGrader}'s question, asked separately and answered from data.
 */
@Component
public class CoreSkillEligibility {

    /**
     * Separators that mean "this name is a list".
     *
     * <p>Spaced deliberately. {@code &} without spaces appears inside {@code AT&T}, and a
     * bare {@code /} inside {@code CI/CD} and {@code TCP/IP} — all single skills whose
     * names happen to contain the character. It is the surrounding whitespace that marks
     * a genuine enumeration.
     */
    private static final Pattern COMPOUND = Pattern.compile("\\s(?:&|and|/|\\+|,|or)\\s",
            Pattern.CASE_INSENSITIVE);

    /**
     * Trailing words that mark a chapter title rather than a skill.
     *
     * <p>Only checked as the <em>last</em> word. {@code Design Patterns} is a heading;
     * {@code Pattern Matching} is a skill, and a substring test would lose the second to
     * catch the first.
     */
    private static final Set<String> HEADING_SUFFIXES = Set.of(
            "fundamentals", "basics", "essentials", "introduction", "overview",
            "techniques", "methodologies", "methodology", "concepts", "topics",
            "tools", "strategies", "patterns", "styles", "principles");

    /**
     * Names that describe a category of skills instead of one.
     *
     * <p>A closed list, and closed on purpose: every entry is a claim that a word is too
     * broad to learn, which is a judgement, so it should be short enough to read in one
     * go and argue with. Each was measured being emitted by the extractor this run.
     *
     * <p>Compared against the whole name, never a substring — {@code Cloud} is a category,
     * {@code Cloud Firestore} is a product.
     */
    private static final Set<String> CATEGORY_WORDS = Set.of(
            "cloud", "cloud computing", "api", "apis", "database", "databases",
            "software", "software development", "software architecture", "software engineering",
            "programming", "coding", "development", "engineering",
            "automation", "testing", "test", "architecture", "design", "integration",
            "monitoring", "performance", "security", "frontend", "front end", "backend",
            "back end", "full stack", "fullstack", "web", "mobile", "data", "analytics",
            "devops", "infrastructure", "networking", "operating systems", "computer science");

    /**
     * Longest a real skill name runs.
     *
     * <p>Past this the string is a sentence — {@code Advanced Data Retrieval & Analytics},
     * {@code Site Reliability Engineering (SRE)}. The compound rule catches most of them;
     * this is the backstop for the ones written without a separator.
     */
    private static final int MAX_NAME_LENGTH = 40;

    /**
     * First characters that mean the string is a fragment of source code, not a name.
     *
     * <p>Measured on the live catalog: 45 rows begin with something other than a letter,
     * and reading them they are almost all imported roadmap node titles that are literally
     * code — MongoDB operators ({@code $match}, {@code $elemMatch}, {@code $unwind}), Sass
     * directives ({@code @if}, {@code @else if}), CLI flags ({@code --watch},
     * {@code -replace option in apply}), annotations ({@code @SpringBootTest Annotation}).
     * They reached the student's skill picker, which offered {@code $and} beside
     * {@code Android}.
     *
     * <p>A leading digit or dot is deliberately NOT here: {@code .NET}, {@code 2D art} and
     * {@code 3D Modeling} are real skills, and the two rows in this catalog that begin with
     * a digit and are not ({@code 1-D Dynamic Programming}, {@code 99.9% Availability})
     * carry no market or curriculum evidence, so the caller's own evidence gate removes
     * them without this rule having to guess.
     */
    private static final Set<Character> CODE_FRAGMENT_PREFIXES =
            Set.of('$', '@', '[', ']', '{', '}', '<', '>', '-', '+', '*', '/', '\\', '#', '%', '=', '!', '?');

    /**
     * True when this name may be graded HIGH for a career.
     *
     * @param skillName the catalog's own spelling; null and blank are never eligible
     */
    public boolean isCoreEligible(String skillName) {
        if (skillName == null) {
            return false;
        }
        String trimmed = skillName.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            return false;
        }
        if (CODE_FRAGMENT_PREFIXES.contains(trimmed.charAt(0))) {
            return false;
        }
        // A question is a lesson title, never a skill name. Twenty catalog rows are
        // phrased this way — "What is a Domain Name?", "What are Data Structures?",
        // "How RDB Works?" — all of them imported roadmap nodes, and one of them had
        // been declared by a student as something they can do.
        if (trimmed.endsWith("?")) {
            return false;
        }
        if (COMPOUND.matcher(trimmed).find()) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (CATEGORY_WORDS.contains(lower)) {
            return false;
        }
        return !endsWithHeadingWord(lower);
    }

    /**
     * True when this name is worth creating a brand-new catalog row for.
     *
     * <p>Same test, different consequence. Skill extraction mints a row for any name it
     * has never seen, which is right for a genuinely new technology and wrong for
     * {@code Software Development} — that one minted 126 rows this run, several of them
     * forks of rows that already existed. A name that could never be graded HIGH is not
     * worth a row at all, so the two questions share an answer.
     */
    public boolean isNameable(String skillName) {
        return isCoreEligible(skillName);
    }

    private boolean endsWithHeadingWord(String lowerName) {
        int lastSpace = lowerName.lastIndexOf(' ');
        if (lastSpace < 0) {
            // A single word that is itself a heading word - "Fundamentals", "Tools" - is
            // caught here rather than being let through for want of a preceding word.
            return HEADING_SUFFIXES.contains(lowerName);
        }
        return HEADING_SUFFIXES.contains(lowerName.substring(lastSpace + 1));
    }
}
