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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

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
                    .name(company.getSignatures() != null && company.getSignatures().has("name") ? company.getSignatures().get("name").asText() : null)
                    .logo(company.getSignatures() != null && company.getSignatures().has("logo") ? company.getSignatures().get("logo").asText() : null)
                    .companyLink(company.getSignatures() != null && company.getSignatures().has("link") ? company.getSignatures().get("link").asText() : null)
                    .build();

            // Flatten the map of tags into a single list
            List<String> flattenedTags = new ArrayList<>();
            if (recruitment.getDescriptions() != null && recruitment.getDescriptions().get("tags") != null) {
                Object tagsObj = recruitment.getDescriptions().get("tags");
                if (tagsObj instanceof String) {
                    flattenedTags.add((String) tagsObj);
                } else if (tagsObj instanceof List) {
                    flattenedTags.addAll((List<String>) tagsObj);
                } else if (tagsObj instanceof Map) {
                    Map<String, List<String>> tagsMap = (Map<String, List<String>>) tagsObj;
                    for (List<String> tagList : tagsMap.values()) {
                        if (tagList != null) {
                            flattenedTags.addAll(tagList);
                        }
                    }
                }
            }

            // Build recruitment DTO
            RecruitmentPostDto.RecruitmentDto recruitmentDto = RecruitmentPostDto.RecruitmentDto.builder()
                    .title(recruitment.getRecruitmentInfos() != null ? (String) recruitment.getRecruitmentInfos().get("title") : null)
                    .salary(recruitment.getRecruitmentInfos() != null ? (String) recruitment.getRecruitmentInfos().get("salary") : null)
                    .location(recruitment.getRecruitmentInfos() != null ? (String) recruitment.getRecruitmentInfos().get("location") : null)
                    .experience(recruitment.getRecruitmentInfos() != null ? (String) recruitment.getRecruitmentInfos().get("experience") : null)
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
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> infosMap = company.getInfos() != null ? mapper.convertValue(company.getInfos(), new TypeReference<Map<String, Object>>(){}) : null;

        return CompanyResponse.builder()
                .companyId(company.getTopCvCompanyId())
                .companyLink(company.getSignatures() != null && company.getSignatures().has("link") ? company.getSignatures().get("link").asText() : null)
                .name(company.getSignatures() != null && company.getSignatures().has("name") ? company.getSignatures().get("name").asText() : null)
                .logo(company.getSignatures() != null && company.getSignatures().has("logo") ? company.getSignatures().get("logo").asText() : null)
                .introductions(company.getInfos() != null && company.getInfos().has("info") ? List.of(company.getInfos().get("info").asText()) : null)
                .infos(infosMap)
                .contacts(company.getInfos() != null && company.getInfos().has("contact") ? List.of(company.getInfos().get("contact").asText()) : null)
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
