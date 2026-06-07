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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CareerService {

    private final CareerRoleRepository careerRoleRepository;
    private final SkillNodeRepository skillNodeRepository;

    public List<CareerResponse> getAllCareers() {
        log.info("Career Module: Fetching all career roles");

        List<CareerRole> careers = careerRoleRepository.findAll();
        
        return careers.stream()
                .map(career -> CareerResponse.builder()
                        .id(career.getCareerId())
                        .roleName(career.getCareerName())
                        .description(career.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    public CareerResponse getCareerRequirements(UUID careerId) {
        log.info("Career Module: Fetching requirements for career ID: {}", careerId);

        Optional<CareerRole> careerRoleOptional = careerRoleRepository.findById(careerId);
        if (careerRoleOptional.isEmpty()) {
            throw new ResourceNotFoundException("Career role not found");
        }

        CareerRole careerRole = careerRoleOptional.get();

        List<SkillNode> nodes = skillNodeRepository.findByCareerRole_CareerId(careerId);

        return CareerResponse.builder()
                .id(careerRole.getCareerId())
                .roleName(careerRole.getCareerName())
                .description(careerRole.getDescription())
                .skillNodes(nodes)
                .build();
    }
}
