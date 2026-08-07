package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.dto.response.student.AiHistoryItemResponse;
import com.inteliroadmap.backend.domain.dto.response.student.DashboardRoadmapProgressResponse;
import com.inteliroadmap.backend.domain.dto.response.student.MarketDemandResponse;
import com.inteliroadmap.backend.domain.dto.response.student.MentorFeedbackItemResponse;
import com.inteliroadmap.backend.domain.dto.response.student.RecommendationItemResponse;
import com.inteliroadmap.backend.domain.dto.response.student.RoadmapStepResponse;
import com.inteliroadmap.backend.domain.dto.response.student.SkillGapItemResponse;
import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import com.inteliroadmap.backend.domain.entity.ChatMessage;
import com.inteliroadmap.backend.domain.entity.ChatSession;
import com.inteliroadmap.backend.domain.entity.Feedback;
import com.inteliroadmap.backend.domain.entity.Skill;
import com.inteliroadmap.backend.domain.entity.SkillNode;
import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
import com.inteliroadmap.backend.repositories.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class StudentDashboardMapper {

    private final SkillRepository skillRepository;

    private static final String CRITICAL_TYPE = "critical";
    private static final String MARKET_TYPE = "market";
    private static final String RECOMMENDATION_TYPE = "Skill Gap";
    private static final String RECOMMENDATION_ICON = "Network";

    public DashboardRoadmapProgressResponse toRoadmapProgressResponse(List<RoadmapStepResponse> steps) {
        return DashboardRoadmapProgressResponse.builder()
                .steps(steps)
                .build();
    }

    public RoadmapStepResponse toRoadmapStepResponse(SkillNode node, RoadmapStepStatus status) {
        return RoadmapStepResponse.builder()
                .id(node.getNodeId())
                .title(node.getNodeName())
                .status(status)
                .parentTitle(node.getParentNode() == null ? null : node.getParentNode().getNodeName())
                .depth(node.getDepth())
                .build();
    }

    public SkillGapItemResponse toSkillGapItemResponse(CareerRequiredSkill requiredSkill, Integer progress) {
        Skill skill = null;
        if (requiredSkill.getSkill().getSkillId() != null) {
            skill = skillRepository.findById(requiredSkill.getSkill().getSkillId()).orElse(null);
        }
        
        return SkillGapItemResponse.builder()
                .id(requiredSkill.getSkill().getSkillId())
                .type(skillGapType(requiredSkill.getImportanceLevel()))
                .title(skill != null ? skill.getSkillName() : "Unknown Skill")
                .description(skill != null ? skillDescription(skill) : "")
                .severity(requiredSkill.getImportanceLevel())
                .progress(progress)
                .build();
    }

    public MentorFeedbackItemResponse toMentorFeedbackItemResponse(Feedback feedback) {
        return MentorFeedbackItemResponse.builder()
                .id(feedback.getFeedbackId())
                .name(feedback.getSenderName())
                .time(formatRelativeTime(feedback.getCreatedAt()))
                .text(feedback.getContent())
                .read(feedback.getStatus() == com.inteliroadmap.backend.domain.enums.FeedbackStatus.READ)
                .build();
    }

    public AiHistoryItemResponse toAiHistoryItemResponse(ChatSession session, ChatMessage message, String content) {
        String tag = session.getSessionName();
        if (tag == null || tag.isBlank()) {
            tag = message.getRole();
        }

        return AiHistoryItemResponse.builder()
                .id(message.getMessageId())
                .tag(tag)
                .title(content)
                .preview(content)
                .build();
    }

    public MarketDemandResponse toMarketDemandResponse(CareerRole careerRole, double growth,
                                                       List<Integer> chart, List<String> chartLabels) {
        return MarketDemandResponse.builder()
                .growth(growth)
                .role(careerRole.getCareerName())
                .chart(chart)
                .chartLabels(chartLabels)
                .build();
    }

    public MarketDemandResponse toEmptyMarketDemandResponse(CareerRole careerRole) {
        return MarketDemandResponse.builder()
                .growth(0)
                .role(careerRole.getCareerName())
                .build();
    }

    public RecommendationItemResponse toRecommendationItemResponse(CareerRequiredSkill requiredSkill) {
        Skill skill = null;
        if (requiredSkill.getSkill().getSkillId() != null) {
            skill = skillRepository.findById(requiredSkill.getSkill().getSkillId()).orElse(null);
        }
        
        return RecommendationItemResponse.builder()
                .id(requiredSkill.getSkill().getSkillId())
                .type(RECOMMENDATION_TYPE)
                .icon(RECOMMENDATION_ICON)
                .title(skill != null ? skill.getSkillName() : "Unknown Skill")
                .description(skill != null ? skillDescription(skill) : "")
                .build();
    }

    public int importanceRank(CareerRequiredSkill requiredSkill) {
        ImportanceLevel importanceLevel = requiredSkill.getImportanceLevel();
        if ("CRITICAL".equalsIgnoreCase(importanceLevel.toString())) {
            return 0;
        }
        if ("HIGH".equalsIgnoreCase(importanceLevel.toString())) {
            return 1;
        }
        return 2;
    }

    private String skillGapType(ImportanceLevel importanceLevel) {
        if ("HIGH".equalsIgnoreCase(importanceLevel.toString()) || "CRITICAL".equalsIgnoreCase(importanceLevel.toString())) {
            return CRITICAL_TYPE;
        }
        return MARKET_TYPE;
    }

    private String skillDescription(Skill skill) {
        if (skill.getCategory() != null && !skill.getCategory().isBlank()) {
            return skill.getCategory();
        }
        if (skill.getCareers() != null && !skill.getCareers().isEmpty()) {
            return skill.getCareers().stream().map(CareerRole::getCareerName).collect(Collectors.joining(", "));
        }
        return null;
    }

    private String formatRelativeTime(LocalDateTime time) {
        if (time == null) {
            return null;
        }

        Duration duration = Duration.between(time, LocalDateTime.now());
        long minutes = Math.max(0, duration.toMinutes());
        if (minutes < 60) {
            return minutes + " minutes ago";
        }

        long hours = duration.toHours();
        if (hours < 24) {
            return hours + " hours ago";
        }

        long days = duration.toDays();
        return days + " days ago";
    }
}
