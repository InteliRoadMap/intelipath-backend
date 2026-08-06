package com.inteliroadmap.backend.components;

import com.inteliroadmap.backend.domain.enums.SeniorityLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a job posting's level from its title and stated experience.
 *
 * <p><b>Title outranks the experience field</b>, and not merely as a tie-break. In
 * this dataset the scraped experience takes only four distinct values across two
 * hundred postings — 116 of them read exactly "3.1 years" — so it is plainly a
 * site-wide figure rather than a per-job one. Trusting it first would file more
 * than half the market into a single band on a signal that never varies. The
 * title, by contrast, names a level explicitly in 44% of postings, and where it
 * does it is the employer's own word for the role.
 *
 * <p>A posting titled "Senior Java Developer" is senior even when the experience
 * field says three years; the reverse — inferring senior from years alone — is how
 * a fresher ends up being shown lead roles.
 *
 * <p>Deliberately keyword rules and a number parse rather than a model: the
 * classification has to be reproducible and explainable, and re-running it must
 * not produce a different market.
 */
@Component
@Slf4j
public class SeniorityClassifier {

    /** Words that settle the level outright, checked against title then experience. */
    private static final String[] FRESHER_WORDS = {
            "intern", "thực tập", "thuc tap", "fresher",
            "no requirement", "no experience", "không yêu cầu", "khong yeu cau",
    };
    private static final String[] SENIOR_WORDS = {
            "senior", "lead", "principal", "architect", "head of", "manager", "expert",
    };
    private static final String[] JUNIOR_WORDS = { "junior" };

    /** "3.1 years", "1-2 năm", "5+ years", "10 months". */
    private static final Pattern YEARS = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*[-–+]?\\s*(\\d+(?:\\.\\d+)?)?");
    private static final Pattern MONTHS_UNIT = Pattern.compile("month|tháng|thang", Pattern.CASE_INSENSITIVE);

    /**
     * @param title      the posting's title, the stronger signal
     * @param experience the stated experience requirement, free text and often
     *                   coarse — used only when the title says nothing
     * @return the level, or {@link SeniorityLevel#UNKNOWN} when neither field
     *         commits to one. UNKNOWN is a real answer here and must not be
     *         hidden from results: a posting we could not read is still a job.
     */
    public SeniorityLevel classify(String title, String experience) {
        String t = normalize(title);

        // Title first, most specific word wins. "Senior" beats "Junior" if a title
        // somehow carries both, because the senior claim is the one a mis-match
        // would mislead a student about.
        if (containsAny(t, FRESHER_WORDS)) return SeniorityLevel.FRESHER;
        if (containsAny(t, SENIOR_WORDS)) return SeniorityLevel.SENIOR;
        if (containsAny(t, JUNIOR_WORDS)) return SeniorityLevel.JUNIOR;

        String e = normalize(experience);
        if (e.isBlank()) return SeniorityLevel.UNKNOWN;
        if (containsAny(e, FRESHER_WORDS)) return SeniorityLevel.FRESHER;
        if (containsAny(e, SENIOR_WORDS)) return SeniorityLevel.SENIOR;
        if (containsAny(e, JUNIOR_WORDS)) return SeniorityLevel.JUNIOR;

        return fromDuration(e);
    }

    /** Bands from an average number of years, per the implementation plan. */
    private SeniorityLevel fromDuration(String experience) {
        Matcher m = YEARS.matcher(experience);
        if (!m.find()) return SeniorityLevel.UNKNOWN;

        double low;
        try {
            low = Double.parseDouble(m.group(1));
        } catch (NumberFormatException ex) {
            return SeniorityLevel.UNKNOWN;
        }
        double high = low;
        if (m.group(2) != null) {
            try {
                high = Double.parseDouble(m.group(2));
            } catch (NumberFormatException ignored) {
                high = low;
            }
        }

        double years = (low + high) / 2.0;
        // "10 months" is under a year; without this it would parse as ten years
        // and file an entry-level posting as senior.
        if (MONTHS_UNIT.matcher(experience).find()) {
            years = years / 12.0;
        }

        if (years < 1) return SeniorityLevel.FRESHER;
        if (years < 3) return SeniorityLevel.JUNIOR;
        if (years < 5) return SeniorityLevel.MID;
        return SeniorityLevel.SENIOR;
    }

    private String normalize(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT).trim();
    }

    private boolean containsAny(String haystack, String[] needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) return true;
        }
        return false;
    }
}
