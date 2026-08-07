package com.inteliroadmap.backend.domain.dto.response.portfolio;

import com.inteliroadmap.backend.domain.enums.EvidenceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioResponse {
    private UserInfoResponse userInfo;
    private PortfolioConfigResponse config;
    private List<StudentSkillResponse> skills;
    private List<PortfolioProjectResponse> projects;
    private List<StudentEducationResponse> education;
    private LearningJourneyResponse learningJourney;
    private PortfolioStudentLevelResponse studentLevel;

}
