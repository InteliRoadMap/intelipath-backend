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

@Component
public class ScraperMapper {
    public RecruitmentPostResponse toRecruitmentPostsResponse(Map<UUID, List<Object>> posts) {
        return RecruitmentPostResponse.builder()
                .postDetails(posts)
                .build();
    }

    public CompanyResponse toCompanyResponse(Company company) {
        return CompanyResponse.builder()
                .companyId(company.getTopCvCompanyId())
                .companyLink(company.getCompanyLink())
                .name(company.getName())
                .logo(company.getLogo())
                .introductions(company.getInfo() != null ? (java.util.List<String>) company.getInfo().get("introduction") : null)
                .infos(company.getInfo())
                .contacts(company.getContact())
                .build();
    }

    public RecruitmentResponse toRecruitmentResponse(Recruitment recruitment) {
        return RecruitmentResponse.builder()
                .topCvRecruitmentId(recruitment.getTopCvRecruitmentId())
                .recruitmentLink(recruitment.getRecruitmentLink())
                .title(recruitment.getBasicInfo() != null ? (String) recruitment.getBasicInfo().get("title") : null)
                .salary(recruitment.getBasicInfo() != null ? (String) recruitment.getBasicInfo().get("salary") : null)
                .location(recruitment.getBasicInfo() != null ? (String) recruitment.getBasicInfo().get("location") : null)
                .experience(recruitment.getBasicInfo() != null ? (String) recruitment.getBasicInfo().get("experience") : null)
                .applicationDeadline(recruitment.getApplicationDeadline())
                .tags(recruitment.getDescriptions() != null ? recruitment.getDescriptions().get("tags") : null)
                .descriptions(recruitment.getDescriptions() != null ? recruitment.getDescriptions() : null)
                .generalInfos(recruitment.getDescriptions() != null ? recruitment.getDescriptions().get("generalInfos") : null)
                .relatedTags(recruitment.getDescriptions() != null ? recruitment.getDescriptions().get("relatedTags") : null)
                .build();
    }
}
