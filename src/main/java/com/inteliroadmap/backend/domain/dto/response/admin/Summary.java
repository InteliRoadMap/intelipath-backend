package com.inteliroadmap.backend.domain.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Summary {
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
