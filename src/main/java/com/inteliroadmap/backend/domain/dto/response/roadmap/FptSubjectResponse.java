package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** One FPT subject in the student-facing checklist, with the student's declared status. */
@Data
@Builder
public class FptSubjectResponse {

    private String code;
    private String name;
    private Integer semester;
    private Integer credits;
    private String prerequisite;

    /** Catalog skills this subject covers (roadmap node names). */
    private List<String> skills;

    /** "PASSED" when the student has declared this subject, else null. */
    private String status;

    /** "CURRICULUM_TERM" or "MANUAL" when declared, else null. */
    private String source;
}
