package com.inteliroadmap.backend.domain.dto.response.student;

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

    /**
     * One label per {@code chart} point, e.g. "27 Jul" for the week starting that
     * Monday.
     *
     * Without these the client had nothing to name the points with and invented
     * {@code ['mon'..'sun'][i % 7]}, so the axis read "sat sun tue" — day names
     * bearing no relation to the dates underneath.
     */
    @Builder.Default
    private List<String> chartLabels = List.of();
}
