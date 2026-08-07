package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class MaterialResponse {
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
