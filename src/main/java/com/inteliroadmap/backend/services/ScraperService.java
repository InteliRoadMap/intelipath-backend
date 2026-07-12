package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.scraper.CompanyResponse;
import com.inteliroadmap.backend.domain.dto.response.scraper.RecruitmentPostDto;
import com.inteliroadmap.backend.domain.dto.response.scraper.RecruitmentResponse;

import java.util.List;

public interface ScraperService {

    List<RecruitmentPostDto> getRecruitmentPosts();

    CompanyResponse getCompanyInfos(String companyId);

    RecruitmentResponse getRecruitmentInfos(String recruitmentId);
}
