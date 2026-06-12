package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.dto.response.student.*;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class StudentDashboardMapper {

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
                .build();
    }

    public SkillGapItemResponse toSkillGapItemResponse(CareerRequiredSkill requiredSkill) {
        Skill skill = requiredSkill.getSkill();
        return SkillGapItemResponse.builder()
                .id(skill.getSkillId())
                .type(skillGapType(requiredSkill.getImportanceLevel()))
                .title(skill.getSkillName())
                .description(skillDescription(skill))
                .severity(requiredSkill.getImportanceLevel())
                .build();
    }

    public MentorFeedbackItemResponse toMentorFeedbackItemResponse(Feedback feedback) {
        return MentorFeedbackItemResponse.builder()
                .id(feedback.getFeedbackId())
                .name(feedback.getSender() != null ? feedback.getSender().getFullName() : null)
                .time(formatRelativeTime(feedback.getCreateAt()))
                .text(feedback.getContent())
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

    public MarketDemandResponse toMarketDemandResponse(CareerRole careerRole, double growth, List<Integer> chart) {
        return MarketDemandResponse.builder()
                .growth(growth)
                .role(careerRole.getCareerName())
                .chart(chart)
                .build();
    }

    public MarketDemandResponse toEmptyMarketDemandResponse(CareerRole careerRole) {
        return MarketDemandResponse.builder()
                .growth(0)
                .role(careerRole.getCareerName())
                .build();
    }

    public RecommendationItemResponse toRecommendationItemResponse(CareerRequiredSkill requiredSkill) {
        Skill skill = requiredSkill.getSkill();
        return RecommendationItemResponse.builder()
                .id(skill.getSkillId())
                .type(RECOMMENDATION_TYPE)
                .icon(RECOMMENDATION_ICON)
                .title(skill.getSkillName())
                .description(skillDescription(skill))
                .build();
    }

    public int importanceRank(CareerRequiredSkill requiredSkill) {
        String importanceLevel = requiredSkill.getImportanceLevel();
        if ("CRITICAL".equalsIgnoreCase(importanceLevel)) {
            return 0;
        }
        if ("HIGH".equalsIgnoreCase(importanceLevel)) {
            return 1;
        }
        return 2;
    }

    private String skillGapType(String importanceLevel) {
        if ("HIGH".equalsIgnoreCase(importanceLevel) || "CRITICAL".equalsIgnoreCase(importanceLevel)) {
            return CRITICAL_TYPE;
        }
        return MARKET_TYPE;
    }

    private String skillDescription(Skill skill) {
        if (skill.getCategory() != null && !skill.getCategory().isBlank()) {
            return skill.getCategory();
        }
        if (skill.getCareer() != null && !skill.getCareer().isBlank()) {
            return skill.getCareer();
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
