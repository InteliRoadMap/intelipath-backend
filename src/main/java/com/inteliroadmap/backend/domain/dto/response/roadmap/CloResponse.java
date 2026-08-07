package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CloResponse {
    /** The syllabus's own label, e.g. CLO1. */
    private String code;
    private String outcome;
}
