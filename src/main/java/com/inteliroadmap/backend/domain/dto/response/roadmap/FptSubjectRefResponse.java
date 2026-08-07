package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FptSubjectRefResponse {
    private String code;
    private String name;
    private Integer semester;
}
