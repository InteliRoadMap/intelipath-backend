package com.inteliroadmap.backend.domain.dto.response.student;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiHistoryItemResponse {
    private UUID id;
    private String tag;
    private String title;
    private String preview;
}
