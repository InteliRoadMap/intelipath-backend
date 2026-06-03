package com.inteliroadmap.backend.domain.dto.response.admin;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserMetricResponse {
    private Long total;
    private Integer growth;
}