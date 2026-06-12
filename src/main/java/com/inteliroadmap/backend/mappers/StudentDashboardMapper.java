package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.dto.response.dashboard.*;
import com.inteliroadmap.backend.domain.entity.*;
import com.inteliroadmap.backend.domain.enums.ImportanceLevel;
import com.inteliroadmap.backend.domain.enums.RoadmapStepStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StudentDashboardMapper {

    private static final String CRITICAL_TYPE = "critical";
    private static final String MARKET_TYPE = "market";
    private static final String RECOMMENDATION_TYPE = "Skill";
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
                .time(feedback.getCreateAt())
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
        if (skill.getCareer() != null && !skill.getCareer().isBlank()) {
            return skill.getCareer();
        }
        return null;
    }
}
