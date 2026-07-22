package com.inteliroadmap.backend.domain.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Progress of an FLM sync job. States mirror the AI service ({@code pending},
 * {@code running}, {@code error}) plus a backend-only terminal {@code imported}
 * emitted once the scraped overlay has been written into the reference tables.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlmSyncStatusResponse {

    private String state;
    private String phase;
    private int done;
    private int total;
    private String message;
    private String error;

    /** Populated only in the terminal {@code imported} state. */
    private Summary summary;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Summary {
        private int subjects;
        private int skillLinks;
        private int unmatchedSkills;
        private int resources;
        private int clos;
        /** Subjects the overlay named but carried no detail for; their stored rows were kept. */
        private int preservedSubjects;
        /**
         * The scrape found subjects but no detail whatsoever — almost always an expired FLM
         * cookie rather than a real result. The import is reported, but not as a success.
         */
        private boolean suspect;
    }
}
