package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.dto.request.PortfolioUpsertRequest;
import com.inteliroadmap.backend.domain.dto.response.PortfolioResponse;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.repositories.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PortfolioMapper {

    private final SkillRepository skillRepository;

    public PortfolioResponse toPortfolioResponse(
            User user, Student student, PortfolioConfig config,
            List<StudentSkill> skills, List<PortfolioProject> projects,
            List<StudentEducation> education) {

        PortfolioResponse.UserInfoResponse userInfo = PortfolioResponse.UserInfoResponse.builder()
                .fullName(user.getFullName())
                .bio(user.getBio())
                .email(user.getEmail())
                .portfolioSlug(student.getPortfolioSlug())
                .university(student.getUniversity())
                .build();

        PortfolioResponse.PortfolioConfigResponse configResponse = null;
        if (config != null) {
            configResponse = PortfolioResponse.PortfolioConfigResponse.builder()
                    .theme(config.getTheme())
                    .themeColors(config.getThemeColors())
                    .fonts(config.getFonts())
                    .heroSection(config.getHeroSection())
                    .skillsSection(config.getSkillsSection())
                    .build();
        }

        List<PortfolioResponse.StudentSkillResponse> skillResponses = skills.stream()
                .map(s -> {
                    String skillName = "Unknown Skill";
                    if (s.getSkill().getSkillId() != null) {
                        Optional<Skill> opt = skillRepository.findById(s.getSkill().getSkillId());
                        if (opt.isPresent()) {
                            skillName = opt.get().getSkillName();
                        }
                    }
                    return PortfolioResponse.StudentSkillResponse.builder()
                            .skillName(skillName)
                            .customDescription(s.getCustomDescription())
                            .techStack(s.getTechStack())
                            .build();
                })
                .collect(Collectors.toList());

        List<PortfolioResponse.PortfolioProjectResponse> projectResponses = projects.stream()
                .map(p -> PortfolioResponse.PortfolioProjectResponse.builder()
                        .projectId(p.getProjectId())
                        .projectName(p.getProjectName())
                        .repoUrl(p.getRepoUrl())
                        .demoUrl(p.getDemoUrl())
                        .description(p.getDescription())
                        .techStack(p.getTechStack())
                        .icon(p.getIcon())
                        .stars(p.getStars())
                        .build())
                .collect(Collectors.toList());

        List<PortfolioResponse.StudentEducationResponse> educationResponses = education.stream()
                .map(e -> PortfolioResponse.StudentEducationResponse.builder()
                        .educationId(e.getEducationId())
                        .university(e.getUniversity())
                        .degree(e.getDegree())
                        .period(e.getPeriod())
                        .description(e.getDescription())
                        .build())
                .collect(Collectors.toList());

        return PortfolioResponse.builder()
                .userInfo(userInfo)
                .config(configResponse)
                .skills(skillResponses)
                .projects(projectResponses)
                .education(educationResponses)
                .build();
    }

    public List<PortfolioProject> toPortfolioProjects(List<PortfolioUpsertRequest.PortfolioProjectRequest> projectRequests, User user) {
        if (projectRequests == null) {
            return Collections.emptyList();
        }
        return projectRequests.stream().map(p ->
                PortfolioProject.builder()
                        .user(com.inteliroadmap.backend.domain.entity.User.builder().userId(user.getUserId()).build())
                        .projectName(p.getProjectName())
                        .repoUrl(p.getRepoUrl())
                        .demoUrl(p.getDemoUrl())
                        .description(p.getDescription())
                        .techStack(p.getTechStack())
                        .icon(p.getIcon())
                        .stars(p.getStars())
                        .build()
        ).collect(Collectors.toList());
    }

    public List<StudentEducation> toStudentEducationList(List<PortfolioUpsertRequest.StudentEducationRequest> educationRequests, Student student) {
        if (educationRequests == null) {
            return Collections.emptyList();
        }
        return educationRequests.stream().map(e ->
                StudentEducation.builder()
                        .user(com.inteliroadmap.backend.domain.entity.Student.builder().userId(student.getUserId()).build())
                        .university(e.getUniversity())
                        .degree(e.getDegree())
                        .period(e.getPeriod())
                        .description(e.getDescription())
                        .build()
        ).collect(Collectors.toList());
    }
}
