package com.inteliroadmap.backend.domain.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminSystemHealthResponse {
    // Overall: "Operational" when every dependency is up, otherwise "Degraded".
    private String status;
    private int servicesUp;
    private int servicesTotal;
    // Per-dependency status so the admin can see exactly which system is down.
    private List<ServiceStatus> services;

    // Extra runtime detail for the dedicated System tab.
    private long uptimeMillis;
    private String version;
    private String javaVersion;
    private DbPool db;
    private Memory memory;

}
