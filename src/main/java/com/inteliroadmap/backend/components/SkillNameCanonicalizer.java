package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.repositories.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The one answer to "are these two names the same skill?".
 *
 * <h2>Why this exists</h2>
 *
 * <p>Three writers put names into the catalog — the seed CSVs, LLM skill extraction from
 * job postings, and GitHub evidence — and until now the only identity function any of
 * them had was {@link SkillRepository#findOneBySkillNameIgnoreCase}, i.e. an exact match
 * modulo case. That is not an identity function, it is a string comparison, and the
 * catalog forked accordingly. Measured on the live database:
 *
 * <ul>
 *   <li>{@code Fast API} (0 postings) beside {@code FastAPI} (12)
 *   <li>{@code React.js} (0) beside {@code React} (69)
 *   <li>{@code Micro-service} and {@code Microservices Architecture} beside
 *       {@code Microservices} (86)
 *   <li>{@code Elastic Search} beside {@code Elasticsearch}, {@code MS SQL} beside
 *       {@code MSSQL}, {@code NextJS} beside {@code Next.js}
 * </ul>
 *
 * <p>Every fork splits one skill's evidence across two rows. A student holding FastAPI
 * matches whichever row their spelling happened to land on and is measured against the
 * other, and 45 {@code career_required_skills} rows sat at HIGH with zero postings —
 * which is the readiness denominator, so a Frontend student's readiness was divided by 26
 * when only 9 of those rows were skills the market had ever named.
 *
 * <p>Note the direction of the fault: the LLM did not cause it. The seed files forked the
 * catalog before extraction ever ran. So the fix cannot live in the extractor's prompt or
 * in the Python alias dict — it has to be one function that every writer goes through.
 *
 * <h2>What the key does and does not do</h2>
 *
 * <p>{@link #matchKey} is for <em>matching only</em>. Display always uses the catalog's
 * own spelling; nothing here rewrites a name a human will read.
 *
 * <p>It deliberately stops short of aggressive stemming. The cost of a false merge is
 * permanent — two real skills collapse into one and the evidence for both is corrupted —
 * while the cost of a missed merge is that the fork survives to be caught later. So the
 * transforms are the ones that provably describe the same token, and everything that
 * needs judgement goes in {@link #ALIASES} where it can be read and argued with.
 *
 * <p>Specifically it must <b>not</b> merge {@code Go}/{@code Golang} or {@code C}/{@code C#}
 * by normalisation — {@code #} and {@code +} survive as part of a name for exactly that
 * reason, and {@code Golang} reaches {@code Go} through the alias table instead, where the
 * decision is explicit. {@code SkillNameCanonicalizerTest} pins both.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SkillNameCanonicalizer {

    /**
     * Shortest token that may be singularised.
     *
     * <p>Above three characters, a trailing {@code s} is far more often a plural than part
     * of the word. At three or below it is usually the name: {@code CSS}, {@code AWS},
     * {@code JS}, {@code K8s}, {@code iOS}. Stripping those would fold {@code CSS} onto
     * {@code CS} and {@code AWS} onto {@code AW}, inventing collisions rather than
     * removing them.
     */
    private static final int MIN_SINGULARISABLE = 4;

    /**
     * Judgement calls normalisation cannot reach, as {@code matchKey -> matchKey}.
     *
     * <p>Kept short on purpose. Every entry here is a claim that two differently-spelled
     * things are one skill, and a wrong claim silently merges evidence, so this is not the
     * place for bulk imports. The entries below are the ones
     * {@code intelipath-service/app/api/endpoints/extraction.py} already asserts for its
     * keyword pass — keeping the two in step matters, because a disagreement means the
     * keyword hit and the LLM hit for one posting land on two different catalog rows.
     *
     * <p>Not loaded: {@code data/v2/skill_aliases_v4.csv} (443 rows, StackOverflow tag
     * synonyms). It contains {@code html -> div}, {@code authentication -> login} and
     * {@code dns -> domain}, which are tag co-occurrences and not skill synonyms. Loading
     * it wholesale would do more damage than the forks it fixes.
     */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            // Kept in step with extraction.py's keyword pass.
            Map.entry("golang", "go"),
            Map.entry("postgre", "postgresql"),      // "Postgres" singularises to "postgre"
            Map.entry("k8s", "kubernete"),           // "Kubernetes" singularises to "kubernete"
            Map.entry("html", "html5"),
            Map.entry("css", "css3"),
            Map.entry("node", "nodejs"),
            Map.entry("reactjs", "react"),
            Map.entry("vuejs", "vue"),
            Map.entry("restapi", "rest"),
            Map.entry("restful", "rest"),

            // Reviewed pair by pair against the posting counts before being written here,
            // because each one is a claim normalisation could not make on its own. The
            // number in the comment is what the canonical row carries.
            Map.entry("restfulapi", "rest"),                                  // REST: 29
            Map.entry("googlecloudplatform", "gcp"),                          // GCP: 11
            Map.entry("googlecloudplatformgcp", "gcp"),
            Map.entry("infrastructureascodeiac", "infrastructureascode"),     // IaC: 7
            Map.entry("sitereliabilityengineeringsre", "sre"),                // SRE: 1
            Map.entry("microservicearchitecture", "microservice"),            // Microservices: 86
            Map.entry("softwarearchitecturefundamental", "softwarearchitecture"),   // 22
            Map.entry("eventdrivenarchitectureeda", "eventdriven"));          // Event-Driven: 1

    private final SkillRepository skillRepository;

    /**
     * Catalog index, built on first use and dropped whenever a row is added.
     *
     * <p>An index rather than a query per name: one extraction run resolves ~5.600 names
     * against ~3.900 rows, and the round trips cost more than the whole table costs to
     * hold. {@code volatile} because the nightly scheduler and an admin trigger can both
     * be in here, and a half-built map must never be visible.
     */
    private volatile Map<String, Skill> index;

    /**
     * The matching key for a skill name, or null when the name is not one.
     *
     * <p>In order: strip accents, drop quoting punctuation, turn separators into spaces,
     * keep only {@code [a-z0-9+# ]}, singularise each token, then remove the spaces. The
     * last step is what makes {@code Fast API} and {@code FastAPI} the same key, and the
     * singularisation before it is what makes {@code Micro-service} and
     * {@code Microservices} the same key.
     */
    public String matchKey(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String folded = Normalizer.normalize(name, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "");

        // Quoting and grouping characters carry no meaning in a skill name. Roadmap node
        // names arrived with backticks around identifiers — "Using `pg_ctl`" beside
        // "Using pg_ctl" — which is 20 catalog forks from punctuation alone.
        folded = folded.replaceAll("[`'\"()\\[\\]]", "");

        // Separators become spaces so tokenisation can see the words; everything else
        // that is not part of a name is dropped. Three characters survive because they
        // are the whole difference between one name and another: '+' and '#' separate C,
        // C++ and C#, and '$' separates MongoDB's `$slice` operator from Go's `Slices`,
        // which the first run of the merge candidates duly proposed joining.
        folded = folded.replaceAll("[-_/&,.]", " ").replaceAll("[^A-Za-z0-9+#$ ]", " ");

        // Case is read before it is thrown away. An all-capitals token is an acronym, and
        // the s on the end of one belongs to the word: HTTPS is not the plural of HTTP.
        StringBuilder key = new StringBuilder();
        for (String token : folded.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            boolean acronym = token.equals(token.toUpperCase(Locale.ROOT))
                    && !token.equals(token.toLowerCase(Locale.ROOT));
            String lower = token.toLowerCase(Locale.ROOT);
            key.append(acronym ? lower : singularise(lower));
        }
        if (key.isEmpty()) {
            return null;
        }
        String result = key.toString();
        return ALIASES.getOrDefault(result, result);
    }

    /**
     * Drops a plural {@code s}, or returns the token unchanged when doing so would be
     * guessing.
     *
     * <p>The guards are for endings where a final {@code s} is part of the word rather
     * than a plural marker: {@code -ss} ({@code Express}, {@code Access}), {@code -us}
     * ({@code Nexus}), {@code -is} ({@code Redis}, {@code Analysis}). Without them
     * {@code Express} becomes {@code expres}, which collides with nothing today but is a
     * fork waiting for someone to type {@code Expres}.
     *
     * <p>Acronyms never reach here at all — {@link #matchKey} checks the token's case
     * before folding it, because {@code HTTPS} is not the plural of {@code HTTP}. That
     * one was caught by running the merge candidates out before writing the migration.
     *
     * <p>{@code -js} is guarded for a sharper reason. The whole point of removing spaces
     * is that {@code Next.js} and {@code NextJS} must land on one key — but the first
     * tokenises to {@code next}+{@code js} and the second to {@code nextjs}, so stripping
     * the plural from the second gives {@code nextj} and re-forks the very pair this was
     * meant to join. Same for React, Vue and Node.
     */
    private String singularise(String token) {
        if (token.length() < MIN_SINGULARISABLE || token.charAt(token.length() - 1) != 's') {
            return token;
        }
        char before = token.charAt(token.length() - 2);
        if (before == 's' || before == 'u' || before == 'i' || before == 'j') {
            return token;
        }
        return token.substring(0, token.length() - 1);
    }

    /**
     * True when both names denote the same skill.
     *
     * <p>Null-safe and reflexive; two unnameable strings are not equal to each other,
     * because "neither of these is a skill" is not a claim that they are the same one.
     */
    public boolean sameSkill(String a, String b) {
        String keyA = matchKey(a);
        return keyA != null && keyA.equals(matchKey(b));
    }

    /**
     * The catalog row for a name, whatever dialect wrote it, or null when the catalog has
     * nothing matching.
     *
     * <p>Falls back to an exact case-insensitive lookup on a miss. The index is a snapshot,
     * and skill extraction mints rows while it runs, so the fallback is what keeps a name
     * minted seconds ago from reading as absent.
     */
    public Skill resolve(String name) {
        String key = matchKey(name);
        if (key == null) {
            return null;
        }
        Skill hit = index().get(key);
        if (hit != null) {
            return hit;
        }
        return skillRepository.findOneBySkillNameIgnoreCase(name.trim());
    }

    /**
     * Forget the cached catalog. Call after inserting a skill, or the row just created
     * stays invisible to {@link #resolve} for the rest of the run.
     */
    public void invalidate() {
        index = null;
    }

    /**
     * Add one freshly-saved row to the cached catalog.
     *
     * <p>{@link #invalidate} is right after a handful of inserts and wrong after a
     * thousand: the seeder creates rows in a loop, and dropping the index each time would
     * rebuild ~3.900 rows per insert. This is the same correction at O(1).
     *
     * <p>Does nothing when the key is already taken — the row that got there first stays
     * canonical, which is what {@link #resolve} would have returned anyway.
     */
    public void remember(Skill skill) {
        if (skill == null) {
            return;
        }
        String key = matchKey(skill.getSkillName());
        Map<String, Skill> current = index;
        if (key != null && current != null) {
            current.putIfAbsent(key, skill);
        }
    }

    private Map<String, Skill> index() {
        Map<String, Skill> current = index;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (index != null) {
                return index;
            }
            List<Skill> all = skillRepository.findAll();
            Map<String, Skill> built = new HashMap<>(all.size() * 2);
            int collisions = 0;
            for (Skill skill : all) {
                String key = matchKey(skill.getSkillName());
                if (key == null) {
                    continue;
                }
                Skill previous = built.putIfAbsent(key, skill);
                if (previous != null) {
                    collisions++;
                    // Deterministic winner: the shorter name, ties broken alphabetically.
                    // Two runs that disagreed on which row is "the" FastAPI would split one
                    // skill's evidence across both, which is the fault this class exists to
                    // end — so the choice must not depend on row order.
                    if (prefer(skill, previous)) {
                        built.put(key, skill);
                    }
                }
            }
            if (collisions > 0) {
                log.info("SkillNameCanonicalizer: catalog holds {} row(s) that are duplicates of "
                        + "another under the matching key; resolving them to a single row. Run the "
                        + "catalog merge migration to remove them for good.", collisions);
            }
            index = built;
            return built;
        }
    }

    /** True when {@code candidate} should win the key over {@code holder}. */
    private boolean prefer(Skill candidate, Skill holder) {
        String a = candidate.getSkillName() == null ? "" : candidate.getSkillName();
        String b = holder.getSkillName() == null ? "" : holder.getSkillName();
        if (a.length() != b.length()) {
            return a.length() < b.length();
        }
        return a.compareTo(b) < 0;
    }
}
