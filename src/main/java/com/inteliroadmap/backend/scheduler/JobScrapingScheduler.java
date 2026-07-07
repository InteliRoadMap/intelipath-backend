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

    @Value("${SCRAPER_LIMIT:20}")
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
        log.info("JobScrapingScheduler: Triggering Python Scraper API at {}", pythonApiUrl);

        try {
            ScraperResponseDto response = restTemplate.getForObject(pythonApiUrl, ScraperResponseDto.class);
            if (response == null) {
                log.warn("JobScrapingScheduler: Received empty response from Scraper API");
                return;
            }

            log.info("JobScrapingScheduler: Received {} companies, {} recruitments, {} posts",
                response.getCompanies().size(), response.getRecruitments().size(), response.getRecruitmentPosts().size());

            // 1. Save Companies (processed shape: signatures + infos)
            for (ScrapedCompanyDto cDto : response.getCompanies()) {
                Company company = companyRepository.findById(cDto.getCompanyId()).orElse(new Company());
                company.setTopCvCompanyId(cDto.getCompanyId());
                company.setSignatures(cDto.getSignatures());
                company.setInfos(cDto.getInfos());
                companyRepository.save(company);
            }

            // 2. Save Recruitments (processed shape: recruitment_infos + descriptions)
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            for (ScrapedRecruitmentDto rDto : response.getRecruitments()) {
                Recruitment recruitment = recruitmentRepository.findById(rDto.getRecruitmentId()).orElse(new Recruitment());
                recruitment.setTopCvRecruitmentId(rDto.getRecruitmentId());
                recruitment.setRecruitmentInfos(rDto.getRecruitmentInfos());
                recruitment.setDescriptions(rDto.getDescriptions());

                if (rDto.getApplicationDeadline() != null) {
                    recruitment.setApplicationDeadline(LocalDate.parse(rDto.getApplicationDeadline(), formatter));
                }

                recruitmentRepository.save(recruitment);
            }

            // 3. Save Recruitment Posts
            for (ScrapedPostDto pDto : response.getRecruitmentPosts()) {
                Company comp = companyRepository.findById(pDto.getCompanyId()).orElse(null);
                Recruitment rec = recruitmentRepository.findById(pDto.getRecruitmentId()).orElse(null);
                
                if (comp != null && rec != null) {
                    RecruitmentPost existingPost = recruitmentPostRepository
                            .findFirstByCompany_TopCvCompanyIdAndRecruitment_TopCvRecruitmentId(comp.getTopCvCompanyId(), rec.getTopCvRecruitmentId());
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
            log.info("JobScrapingScheduler: Successfully persisted scraped data into the database.");

        } catch (Exception e) {
            log.error("JobScrapingScheduler: Failed to scrape jobs from Python API: ", e);
        }
    }
}
