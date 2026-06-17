package com.inteliroadmap.backend.utils;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public class SlugUtils {

    /**
     * Generates a URL-friendly slug from a user's full name and UUID.
     * Formula: [normalized-full-name]-[first-8-chars-of-uuid]
     *
     * @param fullName The user's full name
     * @param userId   The user's UUID
     * @return Generated slug
     */
    public static String generateSlug(String fullName, UUID userId) {
        if (fullName == null || fullName.trim().isEmpty()) {
            fullName = "user";
        }
        
        // Remove accents and normalize characters
        String temp = Normalizer.normalize(fullName, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        temp = pattern.matcher(temp).replaceAll("");
        temp = temp.replace('đ', 'd').replace('Đ', 'D');

        // Replace any non-alphanumeric character with a dash
        temp = temp.replaceAll("[^a-zA-Z0-9-]", "-");
        // Remove duplicate dashes
        temp = temp.replaceAll("-+", "-");
        
        // Remove leading and trailing dashes
        temp = temp.replaceAll("^-|-$", "");

        String uuidSuffix = userId.toString().substring(0, 8);
        return temp.toLowerCase(Locale.ENGLISH) + "-" + uuidSuffix;
    }
}
