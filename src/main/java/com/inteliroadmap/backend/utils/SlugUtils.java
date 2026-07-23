package com.inteliroadmap.backend.utils;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public class SlugUtils {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    /**
     * Turns arbitrary text into the URL-safe part of a slug.
     *
     * Lossy on purpose: two different inputs can normalize to the same output
     * ("vinh.student" and "vinh-student" both become "vinh-student"), so callers
     * that need a unique slug must still check for a collision.
     *
     * @param raw the text to normalize; may be null
     * @return the normalized text, or an empty string if nothing survived
     */
    public static String slugify(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }

        // Remove accents and normalize characters
        String temp = Normalizer.normalize(raw, Normalizer.Form.NFD);
        temp = DIACRITICS.matcher(temp).replaceAll("");
        temp = temp.replace('đ', 'd').replace('Đ', 'D');

        // Replace any non-alphanumeric character with a dash
        temp = temp.replaceAll("[^a-zA-Z0-9-]", "-");
        // Remove duplicate dashes
        temp = temp.replaceAll("-+", "-");

        // Remove leading and trailing dashes
        temp = temp.replaceAll("^-|-$", "");

        return temp.toLowerCase(Locale.ENGLISH);
    }

    /**
     * Generates a URL-friendly slug from a user's full name and UUID.
     * Formula: [normalized-full-name]-[first-8-chars-of-uuid]
     *
     * @param fullName The user's full name
     * @param userId   The user's UUID
     * @return Generated slug
     */
    public static String generateSlug(String fullName, UUID userId) {
        String base = slugify(fullName);
        if (base.isEmpty()) {
            base = "user";
        }

        String uuidSuffix = userId.toString().substring(0, 8);
        return base + "-" + uuidSuffix;
    }
}
