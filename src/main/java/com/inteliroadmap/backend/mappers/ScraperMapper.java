package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.dto.response.scraper.CompanyResponse;
import com.inteliroadmap.backend.domain.dto.response.scraper.RecruitmentResponse;
import com.inteliroadmap.backend.domain.entity.Company;
import com.inteliroadmap.backend.domain.entity.Recruitment;
import com.inteliroadmap.backend.domain.entity.RecruitmentPost;
import com.inteliroadmap.backend.repositories.RecruitmentRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ScraperMapper {

    /** Safe String read from a JSONB map. */
    public static String str(Map<String, Object> map, String key) {
        Object v = map != null ? map.get(key) : null;
        return v != null ? v.toString() : null;
    }

    public CompanyResponse toCompanyResponse(Company company) {
        Map<String, Object> sig = company.getSignatures();
        Map<String, Object> infos = company.getInfos();
        return CompanyResponse.builder()
                .companyId(company.getTopCvCompanyId())
                .companyLink(str(sig, "link"))
                .name(str(sig, "name"))
                .logo(str(sig, "logo"))
                .introductions(null)
                .infos(infos)
                .contacts(str(infos, "contact") != null ? List.of(str(infos, "contact")) : null)
                .build();
    }

    public RecruitmentResponse toRecruitmentResponse(Recruitment recruitment) {
        Map<String, Object> info = recruitment.getRecruitmentInfos();
        Map<String, Object> desc = recruitment.getDescriptions();
        return RecruitmentResponse.builder()
                .topCvRecruitmentId(recruitment.getTopCvRecruitmentId())
                .recruitmentLink(str(info, "link"))
                .title(str(info, "title"))
                .salary(str(info, "salary"))
                .location(str(info, "location"))
                .experience(str(info, "experience"))
                .applicationDeadline(recruitment.getApplicationDeadline())
                .tags(desc != null ? desc.get("tags") : null)
                .descriptions(desc)
                .generalInfos(desc != null ? desc.get("general_infos") : null)
                .relatedTags(desc != null ? desc.get("related_tags") : null)
                .build();
    }
}
