package com.inteliroadmap.backend.domain.dto.response.roadmap;

import lombok.Builder;
import lombok.Data;

/** A single FPT lesson resource (material or session) attached to a roadmap node. */
@Data
@Builder
public class FptNodeResourceResponse {

    private String subjectCode;
    private String kind;
    private String title;
    private String url;
    private String topic;
}
