package com.inteliroadmap.backend.domain.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Returned when an admin starts an FLM sync — the job id to poll for progress. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlmSyncStartResponse {
    private String jobId;
}
