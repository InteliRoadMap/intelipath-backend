package com.inteliroadmap.backend.domain.dto.response.market;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class CompanyTrendResponse {
    private String topCvCompanyId;
    private String name;
    private String logo;
    private String companyLink;
    private int recruitmentCount;
}
