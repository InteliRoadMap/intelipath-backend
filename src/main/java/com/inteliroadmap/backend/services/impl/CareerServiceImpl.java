package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.domain.dto.response.roadmap.CareerResponse;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.repositories.CareerRoleRepository;
import com.inteliroadmap.backend.repositories.SkillNodeRepository;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.services.CareerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service implementation for managing career-related operations.
 * Handles the retrieval of available career roles and their specific skill roadmap nodes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CareerServiceImpl implements CareerService {

    private final CareerRoleRepository careerRoleRepository;
    private final SkillNodeRepository skillNodeRepository;

    /**
     * Get all career roles currently stored in the database.
     *
     * @return list of career role responses
     */
    @Override
    public List<CareerResponse> getAllCareers() {
        log.info("CareerServiceImpl: Career role retrieval request received");

        // Step 1: Load all career roles from the database
        List<CareerRole> careers = careerRoleRepository.findAll();

        // Step 2: Map career entities to API response DTOs
        return careers.stream()
                .map(this::toCareerResponse)
                .toList();
    }

    /**
     * Get a career role and its roadmap skill node requirements.
     *
     * @param careerId database UUID of the requested career role
     * @return career response containing required skill nodes
     */
    @Override
    public CareerResponse getCareerRequirements(UUID careerId) {
        log.info("CareerServiceImpl: Career requirement retrieval request received. careerId: {}", careerId);

        // Step 1: Find the requested career role
        Optional<CareerRole> careerRoleOptional = careerRoleRepository.findById(careerId);
        if (careerRoleOptional.isEmpty()) {
            log.warn("CareerServiceImpl: Career role was not found: {}", careerId);
            throw new ResourceNotFoundException("Career role not found");
        }

        CareerRole careerRole = careerRoleOptional.get();

        // Step 2: Load all roadmap nodes assigned to the career
        List<SkillNode> nodes = skillNodeRepository.findPublishedForCareer(careerId);

        // Step 3: Build the career requirement response
        return CareerResponse.builder()
                .careerId(careerRole.getCareerId())
                .careerName(careerRole.getCareerName())
                .prerequisite(careerRole.getPrerequisite())
                .description(careerRole.getDescription())
                .skillNodes(nodes)
                .build();
    }

    /**
     * Map a career entity to its public response DTO.
     *
     * @param careerRole career entity loaded from the database
     * @return mapped career response
     */
    private CareerResponse toCareerResponse(CareerRole careerRole) {
        // Map entity fields to the response data transfer object
        return CareerResponse.builder()
                .careerId(careerRole.getCareerId())
                .careerName(careerRole.getCareerName())
                .prerequisite(careerRole.getPrerequisite())
                .description(careerRole.getDescription())
                .build();
    }
}
