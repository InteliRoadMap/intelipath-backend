package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.components.CareerAffinityCalculator;
import com.inteliroadmap.backend.domain.dto.response.student.CareerAffinityResponse;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.services.CareerAffinityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CareerAffinityServiceImpl implements CareerAffinityService {

    private final AuthenticatedStudentService authenticatedStudentService;
    private final CareerAffinityCalculator careerAffinityCalculator;

    @Override
    @Transactional(readOnly = true)
    public List<CareerAffinityResponse> rankForCurrentStudent(Integer limit) {
        Student student = authenticatedStudentService.getRequiredStudent();
        UUID currentCareerId = student.getCareerRole() == null
                ? null : student.getCareerRole().getCareerId();

        List<CareerAffinityCalculator.CareerAffinity> ranked =
                careerAffinityCalculator.rank(student.getUserId());

        return ranked.stream()
                .limit(limit == null || limit <= 0 ? ranked.size() : limit)
                .map(affinity -> CareerAffinityResponse.builder()
                        .careerId(affinity.careerId())
                        .careerName(affinity.careerName())
                        .jaccardDistance(affinity.jaccardDistance())
                        .matched(affinity.matched())
                        .required(affinity.required())
                        .topMatchingSkills(affinity.topMatchingSkills())
                        // Marked, not filtered out: seeing where their current choice
                        // ranks is the most useful thing this list can tell a student
                        // who is wondering whether to switch.
                        .current(affinity.careerId().equals(currentCareerId))
                        .build())
                .toList();
    }
}
