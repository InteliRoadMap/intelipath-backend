package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.dto.request.PortfolioUpsertRequest;
import com.inteliroadmap.backend.domain.dto.response.portfolio.PortfolioResponse;
import com.inteliroadmap.backend.domain.entity.PortfolioConfig;
import com.inteliroadmap.backend.domain.entity.PortfolioProject;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentEducation;
import com.inteliroadmap.backend.domain.entity.StudentSkill;
import com.inteliroadmap.backend.domain.entity.StudentSkillEvidence;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.EvidenceType;
import com.inteliroadmap.backend.repositories.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PortfolioMapper {

    private final SkillRepository skillRepository;

    /**
     * @param evidence live evidence rows for the student, used to mark which skills are
     *                 backed by a real source rather than by the student's own word
     */
    public PortfolioResponse toPortfolioResponse(
            User user, Student student, PortfolioConfig config,
            List<StudentSkill> skills, List<PortfolioProject> projects,
            List<StudentEducation> education, List<StudentSkillEvidence> evidence) {

        Map<String, EvidenceType> sourceBySkillName = strongestSourceBySkillName(evidence);

        PortfolioResponse.UserInfoResponse userInfo = PortfolioResponse.UserInfoResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .bio(user.getBio())
                .email(user.getEmail())
                .portfolioSlug(student.getPortfolioSlug())
                .university(student.getUniversityName())
                .avatarUrl(user.getAvatarUrl())
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
                    EvidenceType source = sourceBySkillName.get(skillName.toLowerCase());
                    return PortfolioResponse.StudentSkillResponse.builder()
                            .skillName(skillName)
                            .customDescription(s.getCustomDescription())
                            .techStack(s.getTechStack())
                            .verified(source != null && source != EvidenceType.MANUAL)
                            .evidenceSource(source)
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

    /**
     * Reduces a student's evidence rows to one source per skill. A skill proven by a
     * repository or a transcript outranks the same skill merely declared by the student,
     * so the portfolio shows the strongest claim the student can actually support.
     */
    private Map<String, EvidenceType> strongestSourceBySkillName(List<StudentSkillEvidence> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return Map.of();
        }
        Map<String, EvidenceType> strongest = new HashMap<>();
        for (StudentSkillEvidence row : evidence) {
            if (row.getSkillName() == null || row.getSourceType() == null) {
                continue;
            }
            String key = row.getSkillName().toLowerCase();
            EvidenceType current = strongest.get(key);
            if (current == null || (current == EvidenceType.MANUAL && row.getSourceType() != EvidenceType.MANUAL)) {
                strongest.put(key, row.getSourceType());
            }
        }
        return strongest;
    }

    public List<PortfolioProject> toPortfolioProjects(List<PortfolioUpsertRequest.PortfolioProjectRequest> projectRequests, User user) {
        if (projectRequests == null) {
            return Collections.emptyList();
        }
        return projectRequests.stream().map(p ->
                PortfolioProject.builder()
                        .user(User.builder().userId(user.getUserId()).build())
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
                        .user(Student.builder().userId(student.getUserId()).build())
                        .university(e.getUniversity())
                        .degree(e.getDegree())
                        .period(e.getPeriod())
                        .description(e.getDescription())
                        .build()
        ).collect(Collectors.toList());
    }
}
