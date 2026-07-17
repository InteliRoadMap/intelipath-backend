package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Everything the course-lookup page shows for one FPT subject: the syllabus's own
 * outcomes and the material we hold.
 *
 * Unlike the checklist this is not scoped to the student's combo — the page is for
 * looking up any subject the school teaches, and the FPT-account rule is the gate.
 */
@Data
@Builder
public class FptSubjectDetailResponse {

    private String code;
    private String name;
    private Integer credits;
    private String prerequisite;
    private String description;

    /** Catalog skills this subject covers (roadmap node names). */
    private List<String> skills;

    /** Course Learning Outcomes, verbatim from the syllabus. */
    private List<CloResponse> clos;

    /** Reference list (textbooks, articles) — nothing to download here. */
    private List<MaterialResponse> materials;

    /** Class sessions; the ones with a file are the downloadable material. */
    private List<MaterialResponse> sessions;

    @Data
    @Builder
    public static class CloResponse {
        /** The syllabus's own label, e.g. CLO1. */
        private String code;
        private String outcome;
    }

    @Data
    @Builder
    public static class MaterialResponse {
        private UUID id;
        private String title;
        private String topic;
        /** Which CLO(s) the session maps to, as the syllabus wrote it. */
        private String cloRef;
        /** A reference link the syllabus published. Never a link to the file itself. */
        private String url;
        private Long sizeBytes;
        /**
         * Whether we hold a copy to serve. False is the common case — FLM's own download
         * handler errors for ~145 of 207 files — so the UI must say "no file" rather than
         * offer a button that 404s.
         */
        private boolean downloadable;
    }
}
