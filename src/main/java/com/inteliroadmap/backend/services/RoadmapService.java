package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.UpdateUserProgressRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateNodeProgressRequest;
import com.inteliroadmap.backend.domain.dto.response.StudentRoadmapNodeResponse;
import com.inteliroadmap.backend.domain.dto.response.StudentRoadmapResourceResponse;
import com.inteliroadmap.backend.domain.dto.response.StudentRoadmapResponse;
import com.inteliroadmap.backend.domain.dto.response.RoadmapResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapNodeDto;
import com.inteliroadmap.backend.domain.dto.response.roadmap.SkillGapResponse;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
import com.inteliroadmap.backend.repositories.*;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.security.JwtService;
import com.inteliroadmap.backend.services.RoadmapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public interface RoadmapService {

    public RoadmapResponse getRoadmap(UUID careerId) ;

    public StudentRoadmapResponse getStudentRoadmap() ;

    public RoadmapResponse getRoadmapProgress(UUID careerId) ;

    public RoadmapResponse getNode(UUID nodeId) ;

    public RoadmapResponse updateNodeProgress(UpdateUserProgressRequest request) ;

    public StudentRoadmapResponse getStudentRoadmap(String authHeader) ;

    public StudentRoadmapResponse getCareerRoadmapTemplate(UUID careerId) ;

    public Integer getCareerProgress(UUID careerId, String authHeader) ;

    public RoadmapNodeDto getNodeDetail(UUID nodeId) ;

    public void updateNodeProgress(UpdateNodeProgressRequest request, String authHeader) ;

    public SkillGapResponse compareSkills(String authHeader) ;
}
