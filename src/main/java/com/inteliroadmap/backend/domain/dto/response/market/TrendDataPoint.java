package com.inteliroadmap.backend.domain.dto.response.market;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class TrendDataPoint {
    private LocalDate date;
    private int jobsNeeded;
}
