package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.AdminFlmSyncRequest;
import com.inteliroadmap.backend.domain.dto.response.admin.FlmSyncStartResponse;
import com.inteliroadmap.backend.domain.dto.response.admin.FlmSyncStatusResponse;

/**
 * Admin FLM-sync orchestration: start a scrape job on the AI service, then poll it
 * and, once it finishes, import the scraped overlay into the reference tables exactly
 * once. The admin-supplied cookie is only forwarded, never stored.
 */
public interface AdminFlmSyncService {

    FlmSyncStartResponse start(AdminFlmSyncRequest request);

    FlmSyncStatusResponse poll(String jobId);
}
