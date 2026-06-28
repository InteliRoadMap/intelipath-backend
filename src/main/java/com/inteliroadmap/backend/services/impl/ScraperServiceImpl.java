package com.inteliroadmap.backend.services.impl;
import com.inteliroadmap.backend.services.ScraperService;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScraperServiceImpl implements ScraperService {

    private final CompanyRepository companyRepository;
    private final RecruitmentRepository recruitmentRepository;
    private final RecruitmentPostRepository recruitmentPostRepository;

    public List<RecruitmentPostDto> getRecruitmentPosts() {
        log.info("ScraperService: Retrieving Recruitment Posts...");
        List<RecruitmentPost> posts = recruitmentPostRepository.findAll();
        if(posts.isEmpty()) {
            throw new ResourceNotFoundException("No recruitment posts found");
        }

        List<RecruitmentPostDto> postDtos = new ArrayList<>();
        for (RecruitmentPost post : posts) {
            Company company = post.getCompany();
            Recruitment recruitment = post.getRecruitment();

            RecruitmentPostDto.CompanyDto companyDto = RecruitmentPostDto.CompanyDto.builder()
                    .name(company.getName())
                    .logo(company.getLogo())
                    .companyLink(company.getCompanyLink())
                    .build();

            List<String> flattenedTags = new ArrayList<>();
            if (recruitment.getTags() != null) {
                for (List<String> tagList : recruitment.getTags().values()) {
                    if (tagList != null) {
                        flattenedTags.addAll(tagList);
                    }
                }
            }

            RecruitmentPostDto.RecruitmentDto recruitmentDto = RecruitmentPostDto.RecruitmentDto.builder()
                    .title(recruitment.getTitle())
                    .salary(recruitment.getSalary())
                    .location(recruitment.getLocation())
                    .experience(recruitment.getExperience())
                    .applicationDeadline(recruitment.getApplicationDeadline())
                    .tags(flattenedTags)
                    .build();

            RecruitmentPostDto dto = RecruitmentPostDto.builder()
                    .postId(post.getPostId())
                    .company(companyDto)
                    .recruitment(recruitmentDto)
                    .build();

            postDtos.add(dto);
        }

        log.info("ScraperService: Recruitment Posts Retrieved");
        return postDtos;
    }

    public CompanyResponse getCompanyInfos(String companyId) {
        log.info("ScraperService: Retrieving Company Infos...");
        Company company = companyRepository.findByTopCvCompanyId(companyId);
        if (company == null) {
            throw new ResourceNotFoundException("Company not found");
        }

        log.info("ScraperService: Company Infos Retrieved");
        return CompanyResponse.builder()
                .companyId(company.getTopCvCompanyId())
                .companyLink(company.getCompanyLink())
                .name(company.getName())
                .logo(company.getLogo())
                .introductions(company.getIntroduction())
                .infos(company.getInfo())
                .contacts(company.getContact())
                .build();
    }

    public RecruitmentResponse getRecruitmentInfos(String recruitmentId) {
        log.info("ScraperService: Retrieving Recruitment Infos...");
        Recruitment recruitment = recruitmentRepository.findByTopCvRecruitmentId(recruitmentId);
        if (recruitment == null) {
            throw new ResourceNotFoundException("Recruitment not found");
        }

        log.info("ScraperService: Recruitment Infos Retrieved");
        return RecruitmentResponse.builder()
                .topCvRecruitmentId(recruitment.getTopCvRecruitmentId())
                .recruitmentLink(recruitment.getRecruitmentLink())
                .title(recruitment.getTitle())
                .salary(recruitment.getSalary())
                .location(recruitment.getLocation())
                .experience(recruitment.getExperience())
                .applicationDeadline(recruitment.getApplicationDeadline())
                .tags(recruitment.getTags())
                .descriptions(recruitment.getDescriptions())
                .generalInfos(recruitment.getGeneralInfos())
                .relatedTags(recruitment.getRelatedTags())
                .build();
    }
}
