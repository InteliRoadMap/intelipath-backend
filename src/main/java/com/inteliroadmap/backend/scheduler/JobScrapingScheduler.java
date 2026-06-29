package com.inteliroadmap.backend.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.inteliroadmap.backend.domain.dto.response.scraper.ScraperResponseDto;
import com.inteliroadmap.backend.domain.dto.response.scraper.ScrapedCompanyDto;
import com.inteliroadmap.backend.domain.dto.response.scraper.ScrapedRecruitmentDto;
import com.inteliroadmap.backend.domain.dto.response.scraper.ScrapedPostDto;
import com.inteliroadmap.backend.domain.entity.Company;
import com.inteliroadmap.backend.domain.entity.Recruitment;
import com.inteliroadmap.backend.domain.entity.RecruitmentPost;
import com.inteliroadmap.backend.repositories.CompanyRepository;
import com.inteliroadmap.backend.repositories.RecruitmentRepository;
import com.inteliroadmap.backend.repositories.RecruitmentPostRepository;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobScrapingScheduler {

    @Value("${SCRAPER_API}")
    private String scraperTopCVApi;

    @Value("${SCRAPER_LIMIT}")
    private int scraperLimit;

    private final RestTemplate restTemplate = new RestTemplate();
    private final CompanyRepository companyRepository;
    private final RecruitmentRepository recruitmentRepository;
    private final RecruitmentPostRepository recruitmentPostRepository;

    // Runs every Monday at 9:00 AM
    @Scheduled(cron = "0 0 9 * * Mon")
//    @EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @Transactional
    public void fetchJobsFromPython() {
        int limit = scraperLimit; // Define a default limit or configure it
        String pythonApiUrl = scraperTopCVApi + limit;
        log.info("Triggering Python Scraper API at {}", pythonApiUrl);

        try {
            ScraperResponseDto response = restTemplate.getForObject(pythonApiUrl, ScraperResponseDto.class);
            if (response == null) {
                log.warn("Received empty response from Scraper API");
                return;
            }

            log.info("Received {} companies, {} recruitments, {} posts", 
                response.getCompanies().size(), response.getRecruitments().size(), response.getRecruitmentPosts().size());

            // 1. Save Companies
            for (ScrapedCompanyDto cDto : response.getCompanies()) {
                Company company = companyRepository.findById(cDto.getCompanyId()).orElse(new Company());
                company.setTopCvCompanyId(cDto.getCompanyId());
                company.setCompanyLink(cDto.getCompanyLink());
                company.setLogo(cDto.getLogo());
                company.setName(cDto.getName());
                if (company.getInfo() == null) company.setInfo(new java.util.HashMap<>());
                if (cDto.getInfo() != null) company.getInfo().putAll((java.util.Map) cDto.getInfo());
                if (cDto.getIntroduction() != null) company.getInfo().put("introduction", cDto.getIntroduction());
                company.setContact(cDto.getContact());
                companyRepository.save(company);
            }

            // 2. Save Recruitments
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            for (ScrapedRecruitmentDto rDto : response.getRecruitments()) {
                Recruitment recruitment = recruitmentRepository.findById(rDto.getRecruitmentId()).orElse(new Recruitment());
                recruitment.setTopCvRecruitmentId(rDto.getRecruitmentId());
                recruitment.setRecruitmentLink(rDto.getRecruitmentLink());
                
                if (recruitment.getBasicInfo() == null) recruitment.setBasicInfo(new java.util.HashMap<>());
                if (rDto.getTitle() != null) recruitment.getBasicInfo().put("title", rDto.getTitle());
                if (rDto.getSalary() != null) recruitment.getBasicInfo().put("salary", rDto.getSalary());
                if (rDto.getLocation() != null) recruitment.getBasicInfo().put("location", rDto.getLocation());
                if (rDto.getExperience() != null) recruitment.getBasicInfo().put("experience", rDto.getExperience());

                if (rDto.getApplicationDeadline() != null) {
                    recruitment.setApplicationDeadline(LocalDate.parse(rDto.getApplicationDeadline(), formatter));
                }

                if (recruitment.getDescriptions() == null) recruitment.setDescriptions(new java.util.HashMap<>());
                if (rDto.getTags() != null) recruitment.getDescriptions().put("tags", rDto.getTags());
                if (rDto.getDescriptions() != null) recruitment.getDescriptions().putAll(rDto.getDescriptions());
                if (rDto.getGeneralInfos() != null) recruitment.getDescriptions().put("generalInfos", rDto.getGeneralInfos());
                if (rDto.getRelatedTags() != null) recruitment.getDescriptions().put("relatedTags", rDto.getRelatedTags());

                recruitmentRepository.save(recruitment);
            }

            // 3. Save Recruitment Posts
            for (ScrapedPostDto pDto : response.getRecruitmentPosts()) {
                Company comp = companyRepository.findById(pDto.getCompanyId()).orElse(null);
                Recruitment rec = recruitmentRepository.findById(pDto.getRecruitmentId()).orElse(null);
                
                if (comp != null && rec != null) {
                    RecruitmentPost existingPost = recruitmentPostRepository
                            .findFirstByCompanyIdAndRecruitmentId(comp.getTopCvCompanyId(), rec.getTopCvRecruitmentId());
                    if (existingPost == null) {
                        RecruitmentPost post = new RecruitmentPost();
                        post.setCompany(com.inteliroadmap.backend.domain.entity.Company.builder().topCvCompanyId(comp.getTopCvCompanyId()).build());
                        post.setRecruitment(com.inteliroadmap.backend.domain.entity.Recruitment.builder().topCvRecruitmentId(rec.getTopCvRecruitmentId()).build());
                        if (pDto.getExpireAt() != null) {
                            post.setExpiredAt(LocalDate.parse(pDto.getExpireAt(), formatter));
                        }
                        recruitmentPostRepository.save(post);
                    } else {
                        if (pDto.getExpireAt() != null) {
                            existingPost.setExpiredAt(LocalDate.parse(pDto.getExpireAt(), formatter));
                            recruitmentPostRepository.save(existingPost);
                        }
                    }
                }
            }
            log.info("Successfully persisted scraped data into the database.");
            
        } catch (Exception e) {
            log.error("Failed to scrape jobs from Python API: ", e);
        }
    }
}
