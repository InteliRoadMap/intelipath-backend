package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.CompanyResponse;
import com.inteliroadmap.backend.domain.dto.response.RecruitmentPostResponse;
import com.inteliroadmap.backend.domain.dto.response.RecruitmentResponse;
import com.inteliroadmap.backend.domain.entity.Company;
import com.inteliroadmap.backend.domain.entity.Recruitment;
import com.inteliroadmap.backend.domain.entity.RecruitmentPost;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.mappers.ScraperMapper;
import com.inteliroadmap.backend.repositories.CompanyRepository;
import com.inteliroadmap.backend.repositories.RecruitmentPostRepository;
import com.inteliroadmap.backend.repositories.RecruitmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScraperService {

    public final ScraperMapper scraperMapper;
    private final CompanyRepository companyRepository;
    private final RecruitmentRepository recruitmentRepository;
    private final RecruitmentPostRepository recruitmentPostRepository;

    public RecruitmentPostResponse getRecruitmentPosts() {
        log.info("ScraperService: Retrieving Recruitment Posts...");
        List<RecruitmentPost> posts = recruitmentPostRepository.findAll();
        if(posts.isEmpty()) {
            throw new ResourceNotFoundException("No recruitment posts found");
        }

        Map<UUID, List<Object>> postDetails = new HashMap<>();
        for (RecruitmentPost post : posts) {
            List<Object> detail = new ArrayList<>();
            Company company = post.getCompany();
            Recruitment recruitment = post.getRecruitment();

            // Add company basic info
            detail.add(company.getTopCvCompanyId());
            detail.add(company.getLogo());
            detail.add(company.getName());

            // Add recruitment basic info
            detail.add(recruitment.getTopCvRecruitmentId());
            detail.add(recruitment.getTitle());
            detail.add(recruitment.getSalary());
            detail.add(recruitment.getLocation());
            detail.add(recruitment.getExperience());
            detail.add(recruitment.getApplicationDeadline()); // LocalDate

            // Map post details with postId
            postDetails.put(post.getPostId(), detail);
        }

        log.info("ScraperService: Recruitment Posts Retrieved");
        return scraperMapper.toRecruitmentPostsResponse(postDetails);
    }

    public CompanyResponse getCompanyInfos(String companyId) {
        log.info("ScraperService: Retrieving Company Infos...");
        Company company = companyRepository.findByTopCvCompanyId(companyId);
        if (company == null) {
            throw new ResourceNotFoundException("Company not found");
        }

        log.info("ScraperService: Company Infos Retrieved");
        return scraperMapper.toCompanyResponse(company);
    }

    public RecruitmentResponse getRecruitmentInfos(String recruitmentId) {
        log.info("ScraperService: Retrieving Recruitment Infos...");
        Recruitment recruitment = recruitmentRepository.findByTopCvRecruitmentId(recruitmentId);
        if (recruitment == null) {
            throw new ResourceNotFoundException("Recruitment not found");
        }

        log.info("ScraperService: Recruitment Infos Retrieved");
        return scraperMapper.toRecruitmentResponse(recruitment);
    }

    public void removeOverdueRecruitmentPosts() {
        log.info("ScraperService: Removing Overdue Recruitment Posts...");
        List<RecruitmentPost> posts = recruitmentPostRepository.findAll();

        int count = 0;
        LocalDate today = LocalDate.now();
        for (RecruitmentPost post : posts) {
            if (post.getExpireAt().isBefore(today)) {
                Recruitment recruitment = post.getRecruitment();
                recruitmentPostRepository.removeByRecruitment(recruitment);
                recruitmentRepository.removeByTopCvRecruitmentId(recruitment.getTopCvRecruitmentId());
                count ++;
            }
        }

        log.info("ScraperService: {} Overdue Recruitment Posts Found And Removed", count);
    }
}
