package com.inteliroadmap.backend.domain.dto.response.scraper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecruitmentPostResponse {
    private Map<UUID, List<Object>> postDetails;
}
