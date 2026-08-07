package com.inteliroadmap.backend.domain.dto.response.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SourceReadResponse {
    private String path;
    /** Characters that reached the prompt, after truncation. Zero is a real answer. */
    private int chars;
    private boolean found;
}
