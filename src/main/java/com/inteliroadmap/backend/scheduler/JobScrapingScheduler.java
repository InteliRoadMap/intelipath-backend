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
    @EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
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
                company.setIntroduction(cDto.getIntroduction());
                company.setInfo(cDto.getInfo());
                company.setContact(cDto.getContact());
                companyRepository.save(company);
            }

            // 2. Save Recruitments
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            for (ScrapedRecruitmentDto rDto : response.getRecruitments()) {
                Recruitment recruitment = recruitmentRepository.findById(rDto.getRecruitmentId()).orElse(new Recruitment());
                recruitment.setTopCvRecruitmentId(rDto.getRecruitmentId());
                recruitment.setRecruitmentLink(rDto.getRecruitmentLink());
                recruitment.setTitle(rDto.getTitle());
                recruitment.setSalary(rDto.getSalary());
                recruitment.setLocation(rDto.getLocation());
                recruitment.setExperience(rDto.getExperience());
                if (rDto.getApplicationDeadline() != null) {
                    recruitment.setApplicationDeadline(LocalDate.parse(rDto.getApplicationDeadline(), formatter));
                }
                recruitment.setTags(rDto.getTags());
                recruitment.setDescriptions(rDto.getDescriptions());
                recruitment.setGeneralInfos(rDto.getGeneralInfos());
                recruitment.setRelatedTags(rDto.getRelatedTags());
                recruitmentRepository.save(recruitment);
            }

            // 3. Save Recruitment Posts
            for (ScrapedPostDto pDto : response.getRecruitmentPosts()) {
                Company comp = companyRepository.findById(pDto.getCompanyId()).orElse(null);
                Recruitment rec = recruitmentRepository.findById(pDto.getRecruitmentId()).orElse(null);
                
                if (comp != null && rec != null) {
                    RecruitmentPost existingPost = recruitmentPostRepository.findFirstByCompanyAndRecruitment(comp, rec);
                    if (existingPost == null) {
                        RecruitmentPost post = new RecruitmentPost();
                        post.setCompany(comp);
                        post.setRecruitment(rec);
                        if (pDto.getExpireAt() != null) {
                            post.setExpireAt(LocalDate.parse(pDto.getExpireAt(), formatter));
                        }
                        recruitmentPostRepository.save(post);
                    } else {
                        if (pDto.getExpireAt() != null) {
                            existingPost.setExpireAt(LocalDate.parse(pDto.getExpireAt(), formatter));
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
