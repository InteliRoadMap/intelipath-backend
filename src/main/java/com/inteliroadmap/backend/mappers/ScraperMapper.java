package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.dto.response.CompanyResponse;
import com.inteliroadmap.backend.domain.dto.response.RecruitmentPostResponse;
import com.inteliroadmap.backend.domain.dto.response.RecruitmentResponse;
import com.inteliroadmap.backend.domain.entity.Company;
import com.inteliroadmap.backend.domain.entity.Recruitment;
import com.inteliroadmap.backend.domain.entity.RecruitmentPost;
import com.inteliroadmap.backend.repositories.RecruitmentRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

@Component
public class ScraperMapper {
    public RecruitmentPostResponse toRecruitmentPostsResponse(Map<UUID, List<Object>> posts) {
        return RecruitmentPostResponse.builder()
                .postDetails(posts)
                .build();
    }

    public CompanyResponse toCompanyResponse(Company company) {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> infosMap = company.getInfos() != null ? mapper.convertValue(company.getInfos(), new TypeReference<Map<String, Object>>(){}) : null;

        return CompanyResponse.builder()
                .companyId(company.getTopCvCompanyId())
                .companyLink(company.getSignatures() != null && company.getSignatures().has("link") ? company.getSignatures().get("link").asText() : null)
                .name(company.getSignatures() != null && company.getSignatures().has("name") ? company.getSignatures().get("name").asText() : null)
                .logo(company.getSignatures() != null && company.getSignatures().has("logo") ? company.getSignatures().get("logo").asText() : null)
                .introductions(company.getInfos() != null && company.getInfos().has("info") ? java.util.List.of(company.getInfos().get("info").asText()) : null)
                .infos(infosMap)
                .contacts(company.getInfos() != null && company.getInfos().has("contact") ? java.util.List.of(company.getInfos().get("contact").asText()) : null)
                .build();
    }

    public RecruitmentResponse toRecruitmentResponse(Recruitment recruitment) {
        return RecruitmentResponse.builder()
                .topCvRecruitmentId(recruitment.getTopCvRecruitmentId())
                .recruitmentLink(recruitment.getRecruitmentInfos() != null ? (String) recruitment.getRecruitmentInfos().get("link") : null)
                .title(recruitment.getRecruitmentInfos() != null ? (String) recruitment.getRecruitmentInfos().get("title") : null)
                .salary(recruitment.getRecruitmentInfos() != null ? (String) recruitment.getRecruitmentInfos().get("salary") : null)
                .location(recruitment.getRecruitmentInfos() != null ? (String) recruitment.getRecruitmentInfos().get("location") : null)
                .experience(recruitment.getRecruitmentInfos() != null ? (String) recruitment.getRecruitmentInfos().get("experience") : null)
                .applicationDeadline(recruitment.getApplicationDeadline())
                .tags(recruitment.getDescriptions() != null ? recruitment.getDescriptions().get("tags") : null)
                .descriptions(recruitment.getDescriptions() != null ? recruitment.getDescriptions() : null)
                .generalInfos(recruitment.getDescriptions() != null ? recruitment.getDescriptions().get("general_infos") : null)
                .relatedTags(recruitment.getDescriptions() != null ? recruitment.getDescriptions().get("related_tags") : null)
                .build();
    }
}
