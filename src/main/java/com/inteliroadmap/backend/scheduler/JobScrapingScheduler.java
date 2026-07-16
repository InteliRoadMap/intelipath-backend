package com.inteliroadmap.backend.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.inteliroadmap.backend.ai.client.AiServiceClient;
import com.inteliroadmap.backend.domain.dto.response.scraper.ScraperResponse;
import com.inteliroadmap.backend.domain.dto.response.scraper.ScrapedCompanyResponse;
import com.inteliroadmap.backend.domain.dto.response.scraper.ScrapedRecruitmentResponse;
import com.inteliroadmap.backend.domain.dto.response.scraper.ScrapedPostResponse;
import com.inteliroadmap.backend.domain.entity.Company;
import com.inteliroadmap.backend.domain.entity.Recruitment;
import com.inteliroadmap.backend.domain.entity.RecruitmentPost;
import com.inteliroadmap.backend.repositories.CompanyRepository;
import com.inteliroadmap.backend.repositories.RecruitmentRepository;
import com.inteliroadmap.backend.repositories.RecruitmentPostRepository;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobScrapingScheduler {

    @Value("${SCRAPER_LIMIT:20}")
    private int scraperLimit;

    private final AiServiceClient aiServiceClient;
    private final CompanyRepository companyRepository;
    private final RecruitmentRepository recruitmentRepository;
    private final RecruitmentPostRepository recruitmentPostRepository;
    private final TransactionTemplate transactionTemplate;

    /** Job board the scrape should target. Both feed the same source-agnostic tables. */
    public enum Source { TOPCV, ITVIEC }

    // Runs daily at 9:00 AM
    @Scheduled(cron = "0 0 9 * * *")
    public void fetchJobsFromPython() {
        fetchJobs(Source.TOPCV);
    }

    /**
     * Trigger a scrape for the given source, then persist the processed rows.
     * TopCV and ITviec share the raw tables (ids are prefixed {@code topcv.*} /
     * {@code itviec.*}), so persistence is identical — only the AI-service call differs.
     */
    public void fetchJobs(Source source) {
        int limit = scraperLimit;
        log.info("JobScrapingScheduler: Triggering {} scrape via AI service (limit={})", source, limit);

        try {
            // Network I/O (can take minutes) runs OUTSIDE any transaction so a DB
            // connection is not held from the pool during the whole scrape.
            ScraperResponse response = switch (source) {
                case TOPCV -> aiServiceClient.triggerTopCvScrape(limit);
                case ITVIEC -> aiServiceClient.triggerItviecScrape(limit);
            };
            if (response == null) {
                log.warn("JobScrapingScheduler: Received empty response from Scraper API");
                return;
            }

            log.info("JobScrapingScheduler: Received {} companies, {} recruitments, {} posts",
                response.getCompanies().size(), response.getRecruitments().size(), response.getRecruitmentPosts().size());

            // Persist the fetched data in a single short transaction.
            transactionTemplate.executeWithoutResult(status -> persistScrapedData(response));
            log.info("JobScrapingScheduler: Successfully persisted scraped data into the database.");

        } catch (Exception e) {
            log.error("JobScrapingScheduler: Failed to scrape jobs from Python API: ", e);
        }
    }

    private void persistScrapedData(ScraperResponse response) {
            // 1. Save Companies (processed shape: signatures + infos)
            for (ScrapedCompanyResponse cDto : response.getCompanies()) {
                Company company = companyRepository.findById(cDto.getCompanyId()).orElse(new Company());
                company.setTopCvCompanyId(cDto.getCompanyId());
                company.setSignatures(cDto.getSignatures());
                company.setInfos(cDto.getInfos());
                companyRepository.save(company);
            }

            // 2. Save Recruitments (processed shape: recruitment_infos + descriptions)
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            for (ScrapedRecruitmentResponse rDto : response.getRecruitments()) {
                Recruitment recruitment = recruitmentRepository.findById(rDto.getRecruitmentId()).orElse(new Recruitment());
                recruitment.setTopCvRecruitmentId(rDto.getRecruitmentId());
                recruitment.setRecruitmentInfos(rDto.getRecruitmentInfos());
                recruitment.setDescriptions(rDto.getDescriptions());

                if (rDto.getPostedDate() != null) {
                    recruitment.setPostedDate(LocalDate.parse(rDto.getPostedDate(), formatter));
                }
                if (rDto.getApplicationDeadline() != null) {
                    recruitment.setApplicationDeadline(LocalDate.parse(rDto.getApplicationDeadline(), formatter));
                }

                recruitmentRepository.save(recruitment);
            }

            // 3. Save Recruitment Posts
            for (ScrapedPostResponse pDto : response.getRecruitmentPosts()) {
                Company comp = companyRepository.findById(pDto.getCompanyId()).orElse(null);
                Recruitment rec = recruitmentRepository.findById(pDto.getRecruitmentId()).orElse(null);
                
                if (comp != null && rec != null) {
                    RecruitmentPost existingPost = recruitmentPostRepository
                            .findFirstByCompany_TopCvCompanyIdAndRecruitment_TopCvRecruitmentId(comp.getTopCvCompanyId(), rec.getTopCvRecruitmentId());
                    if (existingPost == null) {
                        RecruitmentPost post = new RecruitmentPost();
                        post.setCompany(Company.builder().topCvCompanyId(comp.getTopCvCompanyId()).build());
                        post.setRecruitment(Recruitment.builder().topCvRecruitmentId(rec.getTopCvRecruitmentId()).build());
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
    }
}
