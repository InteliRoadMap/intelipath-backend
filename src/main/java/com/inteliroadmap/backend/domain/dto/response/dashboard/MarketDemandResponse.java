package com.inteliroadmap.backend.domain.dto.response.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketDemandResponse {
    private double growth;
    private String role;

    @Builder.Default
    private List<Integer> chart = List.of();
}
