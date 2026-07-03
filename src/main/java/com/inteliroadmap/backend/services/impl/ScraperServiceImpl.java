package com.inteliroadmap.backend.services.impl;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the {@link ScraperService} interface.
 * This service is responsible for retrieving scraped recruitment posts and company information.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScraperServiceImpl implements ScraperService {

    private final CompanyRepository companyRepository;
    private final RecruitmentRepository recruitmentRepository;
    private final RecruitmentPostRepository recruitmentPostRepository;

    /**
     * Retrieves all recruitment posts along with their associated company and recruitment details.
     * 
     * @return a list of {@link RecruitmentPostDto} containing recruitment post information
     * @throws ResourceNotFoundException if no recruitment posts are found
     */
    @Override
    public List<RecruitmentPostDto> getRecruitmentPosts() {
        log.info("ScraperServiceImpl: Retrieving Recruitment Posts...");
        // Fetch all recruitment posts from the database
        List<RecruitmentPost> posts = recruitmentPostRepository.findAll();
        if(posts.isEmpty()) {
            throw new ResourceNotFoundException("No recruitment posts found");
        }

        List<RecruitmentPostDto> postDtos = new ArrayList<>();
        // Iterate through each post to build the response DTO
        for (RecruitmentPost post : posts) {
            // Retrieve associated company and recruitment details using their TopCV IDs
            Company company = companyRepository.findByTopCvCompanyId(post.getCompany().getTopCvCompanyId());
            Recruitment recruitment = recruitmentRepository.findByTopCvRecruitmentId(post.getRecruitment().getTopCvRecruitmentId());

            // Build company DTO
            RecruitmentPostDto.CompanyDto companyDto = RecruitmentPostDto.CompanyDto.builder()
                    .name(company.getName())
                    .logo(company.getLogo())
                    .companyLink(company.getCompanyLink())
                    .build();

            // Flatten the map of tags into a single list
            List<String> flattenedTags = new ArrayList<>();
            if (recruitment.getDescriptions() != null && recruitment.getDescriptions().get("tags") != null) {
                Map<String, List<String>> tagsMap = (Map<String, List<String>>) recruitment.getDescriptions().get("tags");
                for (List<String> tagList : tagsMap.values()) {
                    if (tagList != null) {
                        flattenedTags.addAll(tagList);
                    }
                }
            }

            // Build recruitment DTO
            RecruitmentPostDto.RecruitmentDto recruitmentDto = RecruitmentPostDto.RecruitmentDto.builder()
                    .title(recruitment.getBasicInfo() != null ? (String) recruitment.getBasicInfo().get("title") : null)
                    .salary(recruitment.getBasicInfo() != null ? (String) recruitment.getBasicInfo().get("salary") : null)
                    .location(recruitment.getBasicInfo() != null ? (String) recruitment.getBasicInfo().get("location") : null)
                    .experience(recruitment.getBasicInfo() != null ? (String) recruitment.getBasicInfo().get("experience") : null)
                    .applicationDeadline(recruitment.getApplicationDeadline())
                    .tags(flattenedTags)
                    .build();

            // Assemble the final post DTO and add it to the list
            RecruitmentPostDto dto = RecruitmentPostDto.builder()
                    .postId(post.getPostId())
                    .company(companyDto)
                    .recruitment(recruitmentDto)
                    .build();

            postDtos.add(dto);
        }

        log.info("ScraperServiceImpl: Recruitment Posts Retrieved");
        return postDtos;
    }

    /**
     * Retrieves company information by its TopCV company ID.
     *
     * @param companyId the TopCV company ID to retrieve information for
     * @return a {@link CompanyResponse} containing the company details
     * @throws ResourceNotFoundException if the company is not found
     */
    @Override
    public CompanyResponse getCompanyInfos(String companyId) {
        log.info("ScraperServiceImpl: Retrieving Company Infos...");
        Company company = companyRepository.findByTopCvCompanyId(companyId);
        if (company == null) {
            throw new ResourceNotFoundException("Company not found");
        }

        log.info("ScraperServiceImpl: Company Infos Retrieved");
        return CompanyResponse.builder()
                .companyId(company.getTopCvCompanyId())
                .companyLink(company.getCompanyLink())
                .name(company.getName())
                .logo(company.getLogo())
                .introductions(company.getInfo() != null ? (List<String>) company.getInfo().get("introduction") : null)
                .infos(company.getInfo())
                .contacts(company.getContact())
                .build();
    }

    /**
     * Retrieves recruitment information by its TopCV recruitment ID.
     *
     * @param recruitmentId the TopCV recruitment ID to retrieve information for
     * @return a {@link RecruitmentResponse} containing the recruitment details
     * @throws ResourceNotFoundException if the recruitment is not found
     */
    @Override
    public RecruitmentResponse getRecruitmentInfos(String recruitmentId) {
        log.info("ScraperServiceImpl: Retrieving Recruitment Infos...");
        Recruitment recruitment = recruitmentRepository.findByTopCvRecruitmentId(recruitmentId);
        if (recruitment == null) {
            throw new ResourceNotFoundException("Recruitment not found");
        }

        log.info("ScraperServiceImpl: Recruitment Infos Retrieved");
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
