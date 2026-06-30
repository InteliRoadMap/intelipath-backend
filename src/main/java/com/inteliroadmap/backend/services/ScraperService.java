package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.CompanyResponse;
import com.inteliroadmap.backend.domain.dto.response.RecruitmentPostDto;
import com.inteliroadmap.backend.domain.dto.response.RecruitmentResponse;
import com.inteliroadmap.backend.domain.entity.Company;
import com.inteliroadmap.backend.domain.entity.Recruitment;
import com.inteliroadmap.backend.domain.entity.RecruitmentPost;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.CompanyRepository;
import com.inteliroadmap.backend.repositories.RecruitmentPostRepository;
import com.inteliroadmap.backend.repositories.RecruitmentRepository;
import com.inteliroadmap.backend.services.ScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

public interface ScraperService {

    public List<RecruitmentPostDto> getRecruitmentPosts() ;

    public CompanyResponse getCompanyInfos(String companyId) ;

    public RecruitmentResponse getRecruitmentInfos(String recruitmentId) ;
}
