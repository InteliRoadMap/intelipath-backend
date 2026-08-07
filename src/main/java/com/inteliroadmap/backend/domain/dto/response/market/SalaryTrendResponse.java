package com.inteliroadmap.backend.domain.dto.response.market;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class SalaryTrendResponse {
    private String category; // e.g., "0-10 triệu", "10-20 triệu", "20-30 triệu", "30-50 triệu", ">50 triệu"
    private int jobCount;
}
