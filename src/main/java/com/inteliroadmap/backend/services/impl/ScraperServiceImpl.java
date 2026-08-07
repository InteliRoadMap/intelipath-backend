package com.inteliroadmap.backend.services.impl;


import static com.inteliroadmap.backend.mappers.ScraperMapper.str;
import com.inteliroadmap.backend.domain.dto.response.scraper.CompanyResponse;
import com.inteliroadmap.backend.domain.dto.response.scraper.RecruitmentPostResponse;
import com.inteliroadmap.backend.domain.dto.response.scraper.RecruitmentResponse;
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
     * @return a list of {@link RecruitmentPostResponse} containing recruitment post information
     * @throws ResourceNotFoundException if no recruitment posts are found
     */
    @Override
    public List<RecruitmentPostResponse> getRecruitmentPosts() {
        return getRecruitmentPosts(null);
    }

    @Override
    public List<RecruitmentPostResponse> getRecruitmentPosts(String seniority) {
        return getRecruitmentPosts(seniority, null);
    }

    @Override
    public List<RecruitmentPostResponse> getRecruitmentPosts(String seniority, java.util.UUID careerId) {
        log.info("ScraperServiceImpl: Retrieving Recruitment Posts (level filter: {})",
                seniority == null ? "none" : seniority);
        // Fetch all recruitment posts from the database. An empty result is a
        // valid state (nothing scraped yet) -> return an empty list, not a 404.
        List<RecruitmentPost> posts = recruitmentPostRepository.findAll();

        // Sorted at the end rather than here: post.getRecruitment() is a lazy proxy
        // and this method runs outside a transaction, so reading postedDate off it
        // before the loop has fetched the real row would throw. The loop already
        // loads each Recruitment, so the date is free once we are inside it.
        List<PostWithDate> dated = new ArrayList<>();
        List<RecruitmentPostResponse> postDtos = new ArrayList<>();
        // Iterate through each post to build the response DTO
        for (RecruitmentPost post : posts) {
            // Retrieve associated company and recruitment details using their TopCV IDs
            Company company = companyRepository.findByTopCvCompanyId(post.getCompany().getTopCvCompanyId());
            Recruitment recruitment = recruitmentRepository.findByTopCvRecruitmentId(post.getRecruitment().getTopCvRecruitmentId());
            if (careerId != null && !careerId.equals(recruitment.getCareerId())) continue;

            // Build company DTO
            com.inteliroadmap.backend.domain.dto.response.scraper.Company companyDto =
                    com.inteliroadmap.backend.domain.dto.response.scraper.Company.builder()
                    .name(str(company.getSignatures(), "name"))
                    .logo(str(company.getSignatures(), "logo"))
                    .companyLink(str(company.getSignatures(), "link"))
                    .build();

            // descriptions.tags is an AI-summarised string (older data may hold a map/list).
            List<String> flattenedTags = new ArrayList<>();
            Object tagsObj = recruitment.getDescriptions() != null ? recruitment.getDescriptions().get("tags") : null;
            if (tagsObj instanceof String s && !s.isBlank()) {
                flattenedTags.add(s);
            } else if (tagsObj instanceof List<?> list) {
                for (Object t : list) {
                    if (t != null) flattenedTags.add(t.toString());
                }
            }

            // Build recruitment DTO
            com.inteliroadmap.backend.domain.dto.response.scraper.Recruitment recruitmentDto =
                    com.inteliroadmap.backend.domain.dto.response.scraper.Recruitment.builder()
                    .title(str(recruitment.getRecruitmentInfos(), "title"))
                    .salary(str(recruitment.getRecruitmentInfos(), "salary"))
                    .location(str(recruitment.getRecruitmentInfos(), "location"))
                    .experience(str(recruitment.getRecruitmentInfos(), "experience"))
                    .seniority(recruitment.getSeniority())
                    .applicationDeadline(recruitment.getApplicationDeadline())
                    .tags(flattenedTags)
                    .build();

            // Assemble the final post DTO and add it to the list
            RecruitmentPostResponse dto = RecruitmentPostResponse.builder()
                    .postId(post.getPostId())
                    .company(companyDto)
                    .recruitment(recruitmentDto)
                    .build();

            // UNKNOWN survives the filter on purpose; see getRecruitmentPosts(String).
            if (seniority != null
                    && recruitment.getSeniority() != null
                    && !"UNKNOWN".equalsIgnoreCase(recruitment.getSeniority())
                    && !seniority.equalsIgnoreCase(recruitment.getSeniority())) {
                continue;
            }

            dated.add(new PostWithDate(recruitment.getPostedDate(), dto));
        }

        // Newest first. findAll() returns heap order — neither newest nor anything
        // else a reader could name — so the top of the "open roles" list meant
        // nothing. Undated rows sink to the bottom rather than being dropped.
        dated.sort(java.util.Comparator.comparing(PostWithDate::postedDate,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        for (PostWithDate entry : dated) {
            postDtos.add(entry.dto());
        }

        log.info("ScraperServiceImpl: Recruitment Posts Retrieved, newest first");
        return postDtos;
    }

    /** A built response paired with the date it should be ordered by. */
    private record PostWithDate(java.time.LocalDate postedDate, RecruitmentPostResponse dto) {}

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
                .companyLink(str(company.getSignatures(), "link"))
                .name(str(company.getSignatures(), "name"))
                .logo(str(company.getSignatures(), "logo"))
                .introductions(null)
                .infos(company.getInfos())
                .contacts(str(company.getInfos(), "contact") != null ? List.of(str(company.getInfos(), "contact")) : null)
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
                .recruitmentLink(str(recruitment.getRecruitmentInfos(), "link"))
                .title(str(recruitment.getRecruitmentInfos(), "title"))
                .salary(str(recruitment.getRecruitmentInfos(), "salary"))
                .location(str(recruitment.getRecruitmentInfos(), "location"))
                .experience(str(recruitment.getRecruitmentInfos(), "experience"))
                .applicationDeadline(recruitment.getApplicationDeadline())
                .tags(recruitment.getDescriptions() != null ? recruitment.getDescriptions().get("tags") : null)
                .descriptions(recruitment.getDescriptions())
                .generalInfos(recruitment.getDescriptions() != null ? recruitment.getDescriptions().get("general_infos") : null)
                .relatedTags(recruitment.getDescriptions() != null ? recruitment.getDescriptions().get("related_tags") : null)
                .build();
    }
}
