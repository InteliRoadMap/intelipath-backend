package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.CareerResponse;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.SkillNodeRepository;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CareerService {

    public List<CareerResponse> getAllCareers() ;

    public CareerResponse getCareerRequirements(UUID careerId) ;
}
