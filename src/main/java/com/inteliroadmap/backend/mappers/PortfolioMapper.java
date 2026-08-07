package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.dto.request.PortfolioProjectRequest;
import com.inteliroadmap.backend.domain.dto.request.StudentEducationRequest;
import com.inteliroadmap.backend.domain.dto.response.portfolio.PortfolioConfigResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.PortfolioProjectResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.StudentEducationResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.StudentSkillResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.UserInfoResponse;

import com.inteliroadmap.backend.domain.dto.request.PortfolioUpsertRequest;
import com.inteliroadmap.backend.domain.dto.response.portfolio.PortfolioResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.CoreSkillSummaryResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.LearningJourneyResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.RoadmapStageResponse;
import com.inteliroadmap.backend.domain.dto.response.portfolio.PortfolioStudentLevelResponse;
import com.inteliroadmap.backend.domain.dto.response.student.StudentLevelResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.CoreSkillResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapNodeResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.StudentRoadmapResponse;
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
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.UUID;

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
            List<StudentEducation> education, List<StudentSkillEvidence> evidence,
            StudentRoadmapResponse roadmap, StudentLevelResponse studentLevel) {

        Map<UUID, EvidenceType> sourceByStudentSkillId = strongestSourceByStudentSkillId(evidence);

        UserInfoResponse userInfo = UserInfoResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .bio(user.getBio())
                .email(user.getEmail())
                .portfolioSlug(student.getPortfolioSlug())
                .university(student.getUniversityName())
                .avatarUrl(user.getAvatarUrl())
                .build();

        PortfolioConfigResponse configResponse = null;
        if (config != null) {
            configResponse = PortfolioConfigResponse.builder()
                    .theme(config.getTheme())
                    .themeColors(config.getThemeColors())
                    .fonts(config.getFonts())
                    .heroSection(config.getHeroSection())
                    .skillsSection(config.getSkillsSection())
                    .build();
        }

        List<StudentSkillResponse> skillResponses = skills.stream()
                .map(s -> {
                    String skillName = "Unknown Skill";
                    if (s.getSkill().getSkillId() != null) {
                        Optional<Skill> opt = skillRepository.findById(s.getSkill().getSkillId());
                        if (opt.isPresent()) {
                            skillName = opt.get().getSkillName();
                        }
                    }
                    EvidenceType source = sourceByStudentSkillId.get(s.getStudentSkillId());
                    return StudentSkillResponse.builder()
                            .skillName(skillName)
                            .customDescription(s.getCustomDescription())
                            .techStack(s.getTechStack())
                            // StudentSkill.verifiedBy is the canonical verification state used
                            // by readiness, level and the roadmap. Evidence supplies the label,
                            // but must not independently redefine whether the skill is verified.
                            .verified(s.getVerifiedBy() != null && !s.getVerifiedBy().isBlank())
                            .evidenceSource(source)
                            .build();
                })
                .collect(Collectors.toList());

        List<PortfolioProjectResponse> projectResponses = projects.stream()
                .map(p -> PortfolioProjectResponse.builder()
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

        List<StudentEducationResponse> educationResponses = education.stream()
                .map(e -> StudentEducationResponse.builder()
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
                .learningJourney(toLearningJourney(roadmap))
                .studentLevel(toPortfolioStudentLevel(studentLevel))
                .build();
    }

    private PortfolioStudentLevelResponse toPortfolioStudentLevel(StudentLevelResponse level) {
        if (level == null || level.getLevel() == null) return null;
        return PortfolioStudentLevelResponse.builder()
                .level(level.getLevel())
                .source(level.getSource())
                .assessedAt(level.getAssessedAt())
                .build();
    }

    private LearningJourneyResponse toLearningJourney(StudentRoadmapResponse roadmap) {
        if (roadmap == null || roadmap.getTargetCareerRole() == null) return null;

        List<CoreSkillSummaryResponse> coreSkills = roadmap.getCoreSkills() == null
                ? List.of()
                : roadmap.getCoreSkills().stream().map(this::toCoreSkillSummary).toList();

        Map<String, int[]> stageCounts = new LinkedHashMap<>();
        for (RoadmapNodeResponse node : roadmap.getNodes() == null ? List.<RoadmapNodeResponse>of() : roadmap.getNodes()) {
            String stage = node.getStage() == null || node.getStage().isBlank() ? "Roadmap" : node.getStage();
            int[] counts = stageCounts.computeIfAbsent(stage, ignored -> new int[3]);
            counts[0]++;
            if ("completed".equalsIgnoreCase(node.getStatus())) counts[1]++;
            if ("current".equalsIgnoreCase(node.getStatus())) counts[2]++;
        }
        List<RoadmapStageResponse> stages = stageCounts.entrySet().stream()
                .map(entry -> RoadmapStageResponse.builder()
                        .name(entry.getKey()).totalNodes(entry.getValue()[0])
                        .completedNodes(entry.getValue()[1]).currentNodes(entry.getValue()[2]).build())
                .toList();

        return LearningJourneyResponse.builder()
                .targetCareerRole(roadmap.getTargetCareerRole()).progress(roadmap.getProgress())
                .readiness(roadmap.getReadiness()).readinessVerified(roadmap.getReadinessVerified())
                .readinessRequiredCount(roadmap.getReadinessRequiredCount())
                .readinessHeldCount(roadmap.getReadinessHeldCount())
                .readinessVerifiedCount(roadmap.getReadinessVerifiedCount())
                .coreSkills(coreSkills).stages(stages).build();
    }

    private CoreSkillSummaryResponse toCoreSkillSummary(CoreSkillResponse skill) {
        Integer jobCount = skill.getMarketDemand() == null ? null : skill.getMarketDemand().getJobCount();
        return CoreSkillSummaryResponse.builder()
                .skillName(skill.getSkillName()).importance(skill.getImportance())
                .proficiency(skill.getProficiency()).verifiedBy(skill.getVerifiedBy())
                .marketJobCount(jobCount).build();
    }

    /**
     * Reduces a student's evidence rows to one source per skill. A skill proven by a
     * repository or a transcript outranks the same skill merely declared by the student,
     * so the portfolio shows the strongest claim the student can actually support.
     */
    private Map<UUID, EvidenceType> strongestSourceByStudentSkillId(List<StudentSkillEvidence> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return Map.of();
        }
        Map<UUID, EvidenceType> strongest = new HashMap<>();
        for (StudentSkillEvidence row : evidence) {
            if (row.getStudentSkillId() == null || row.getSourceType() == null) {
                continue;
            }
            UUID key = row.getStudentSkillId();
            EvidenceType current = strongest.get(key);
            if (current == null || (current == EvidenceType.MANUAL && row.getSourceType() != EvidenceType.MANUAL)) {
                strongest.put(key, row.getSourceType());
            }
        }
        return strongest;
    }

    public List<PortfolioProject> toPortfolioProjects(List<PortfolioProjectRequest> projectRequests, User user) {
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

    public List<StudentEducation> toStudentEducationList(List<StudentEducationRequest> educationRequests, Student student) {
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
