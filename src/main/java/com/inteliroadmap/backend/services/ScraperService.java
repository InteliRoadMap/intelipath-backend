package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.CompanyResponse;
import com.inteliroadmap.backend.domain.dto.response.RecruitmentPostDto;
import com.inteliroadmap.backend.domain.dto.response.RecruitmentResponse;

import java.util.List;

public interface ScraperService {

    List<RecruitmentPostDto> getRecruitmentPosts() ;

    CompanyResponse getCompanyInfos(String companyId) ;

    RecruitmentResponse getRecruitmentInfos(String recruitmentId) ;
}
