package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.UpdateUserProgressRequest;
import com.inteliroadmap.backend.domain.dto.request.UpdateNodeProgressRequest;
import com.inteliroadmap.backend.domain.dto.response.roadmap.StudentRoadmapResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapNodeDto;
import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
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

    StudentRoadmapResponse getStudentRoadmap();

    StudentRoadmapResponse getCareerRoadmapTemplate(UUID careerId);

    Integer getCareerProgress(UUID careerId);

    RoadmapNodeDto getNodeDetail(UUID nodeId);

    void updateNodeProgress(UpdateNodeProgressRequest request);
}
