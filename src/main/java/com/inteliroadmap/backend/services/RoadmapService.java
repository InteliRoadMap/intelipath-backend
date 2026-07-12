package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.UpdateNodeProgressRequest;
import com.inteliroadmap.backend.domain.dto.response.roadmap.StudentRoadmapResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapNodeResponse;

import java.util.UUID;

public interface RoadmapService {

    StudentRoadmapResponse getStudentRoadmap();

    StudentRoadmapResponse getCareerRoadmapTemplate(UUID careerId);

    Integer getCareerProgress(UUID careerId);

    RoadmapNodeResponse getNodeDetail(UUID nodeId);

    void updateNodeProgress(UpdateNodeProgressRequest request);
}
