package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.response.SkillResponse;
import com.inteliroadmap.backend.services.dashboard.StudentDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roadmap/skills")
@RequiredArgsConstructor
@Slf4j
public class RoadmapSkillController {

    private final StudentDashboardService studentDashboardService;

    /**
     * Compare authenticated student's selected skills with required career skills.
     *
     * @return skill comparison response
     */
    @PostMapping("/compare")
    public ResponseEntity<SkillResponse> compareSkills() {
        log.info("Roadmap Skills API: Compare skills request received");
        return ResponseEntity.ok(studentDashboardService.compareCurrentStudentSkills());
    }
}
