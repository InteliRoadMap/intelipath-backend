package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.UpdateUserProgressRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateNodeProgressRequest;
import com.inteliroadmap.backend.domain.dto.response.roadmap.StudentRoadmapResponse;
import com.inteliroadmap.backend.domain.dto.response.RoadmapResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapNodeDto;
import com.inteliroadmap.backend.domain.dto.response.roadmap.SkillGapResponse;

import java.util.UUID;

public interface RoadmapService {

    RoadmapResponse getRoadmap(UUID careerId) ;

    StudentRoadmapResponse getStudentRoadmap() ;

    RoadmapResponse getRoadmapProgress(UUID careerId) ;

    RoadmapResponse getNode(UUID nodeId) ;

    RoadmapResponse updateNodeProgress(UpdateUserProgressRequest request) ;

    StudentRoadmapResponse getStudentRoadmap(String authHeader) ;

    StudentRoadmapResponse getCareerRoadmapTemplate(UUID careerId) ;

    Integer getCareerProgress(UUID careerId, String authHeader) ;

    RoadmapNodeDto getNodeDetail(UUID nodeId) ;

    void updateNodeProgress(UpdateNodeProgressRequest request, String authHeader) ;

    SkillGapResponse compareSkills(String authHeader) ;
}
