package com.inteliroadmap.backend.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inteliroadmap.backend.ai.client.AiServiceClient;
import com.inteliroadmap.backend.ai.config.AiServiceProperties;
import com.inteliroadmap.backend.domain.dto.response.scraper.ScraperResponse;
import com.inteliroadmap.backend.domain.dto.response.scraper.ScrapedCompanyResponse;
import com.inteliroadmap.backend.domain.dto.response.scraper.ScrapedRecruitmentResponse;
import com.inteliroadmap.backend.domain.dto.response.scraper.ScrapedPostResponse;
import com.inteliroadmap.backend.domain.entity.Company;
import com.inteliroadmap.backend.domain.entity.Recruitment;
import com.inteliroadmap.backend.domain.entity.RecruitmentPost;
import com.inteliroadmap.backend.repositories.CompanyRepository;
import com.inteliroadmap.backend.components.SeniorityClassifier;
import com.inteliroadmap.backend.repositories.RecruitmentRepository;
import com.inteliroadmap.backend.repositories.RecruitmentPostRepository;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobScrapingScheduler {

    // Job posts pulled per scrape, by the daily run and the admin trigger alike. Must not
    // exceed the AI service's max_scrape_limit, which rejects a bigger number with a 400
    // instead of clamping to it — raising this alone would break the scrape outright.
    // At ~1s per posting (a detail fetch plus the politeness delay) 200 takes roughly four
    // minutes, well inside the 15-minute scrape timeout.
    @Value("${SCRAPER_LIMIT:200}")
    private int scraperLimit;

    private final AiServiceClient aiServiceClient;
    private final AiServiceProperties aiServiceProperties;
    private final ObjectMapper objectMapper;
    private final CompanyRepository companyRepository;
    private final RecruitmentRepository recruitmentRepository;
    private final RecruitmentPostRepository recruitmentPostRepository;
    private final SeniorityClassifier seniorityClassifier;
    private final TransactionTemplate transactionTemplate;

    /** Job board the scrape should target. Both feed the same source-agnostic tables. */
    public enum Source { TOPCV, ITVIEC }

    // Runs daily at 9:00 AM
    @Scheduled(cron = "0 0 9 * * *")
    public void fetchJobsFromPython() {
        // Scheduled runs must never propagate — just log and move on.
        try {
            fetchJobs(Source.ITVIEC);
        } catch (Exception e) {
            log.error("JobScrapingScheduler: Scheduled scrape failed: ", e);
        }
    }

    /**
     * Trigger a scrape for the given source, then persist the processed rows.
     * TopCV and ITviec share the raw tables (ids are prefixed {@code topcv.*} /
     * {@code itviec.*}), so persistence is identical — only the AI-service call differs.
     *
     * <p>Failures (timeouts, Cloudflare blocks, ...) are propagated so a manual
     * caller learns the scrape did not complete instead of getting a false "success".
     *
     * @return number of recruitment posts persisted.
     */
    public int fetchJobs(Source source) {
        int limit = scraperLimit;
        log.info("JobScrapingScheduler: Triggering {} scrape via AI service (limit={})", source, limit);

        // Network I/O (can take tens of minutes) runs OUTSIDE any transaction so a DB
        // connection is not held from the pool during the whole scrape.
        ScraperResponse response = awaitScrape(source, limit);
        if (response == null) {
            log.warn("JobScrapingScheduler: Received empty response from Scraper API");
            return 0;
        }

        log.info("JobScrapingScheduler: Received {} companies, {} recruitments, {} posts",
            response.getCompanies().size(), response.getRecruitments().size(), response.getRecruitmentPosts().size());

        // Persist the fetched data in a single short transaction.
        transactionTemplate.executeWithoutResult(status -> persistScrapedData(response));
        log.info("JobScrapingScheduler: Successfully persisted scraped data into the database.");
        return response.getRecruitmentPosts().size();
    }

    /**
     * Start the scrape as a background job on the AI service and poll until it finishes.
     *
     * <p>It used to be one blocking request that only answered once everything was scraped
     * and summarised. At a couple hundred postings that runs past the read timeout, and the
     * completed result — already sitting in the AI service's own database — was discarded
     * because nothing was left to receive it. Polling costs one cheap call every few seconds
     * and survives a run of any length up to the configured budget.
     *
     * @return the scraped payload, or {@code null} if the job failed or never finished.
     */
    private ScraperResponse awaitScrape(Source source, int limit) {
        String jobId = aiServiceClient.startScrape(source.name().toLowerCase(), limit);
        if (jobId == null || jobId.isBlank()) {
            log.warn("JobScrapingScheduler: AI service did not return a scrape job id");
            return null;
        }

        Duration budget = aiServiceProperties.getScrapeJobTimeout();
        Duration interval = aiServiceProperties.getScrapePollInterval();
        log.info("JobScrapingScheduler: {} scrape queued as job {} (budget={})", source, jobId, budget);

        Instant deadline = Instant.now().plus(budget);
        String lastMessage = null;

        while (Instant.now().isBefore(deadline)) {
            try {
                Thread.sleep(interval.toMillis());
            } catch (InterruptedException e) {
                // Preserve the flag so a shutdown actually stops this thread.
                Thread.currentThread().interrupt();
                log.warn("JobScrapingScheduler: Interrupted while awaiting scrape job {}", jobId);
                return null;
            }

            JsonNode status = aiServiceClient.getScrapeStatus(jobId);
            if (status == null) {
                log.warn("JobScrapingScheduler: Empty status for scrape job {}", jobId);
                continue;
            }

            String state = status.path("state").asText("");
            String message = status.path("message").asText("");
            // Only log when the job actually reports something new, so a long scrape
            // doesn't fill the log with identical lines every few seconds.
            if (!message.isBlank() && !message.equals(lastMessage)) {
                log.info("JobScrapingScheduler: scrape job {} [{}] {}", jobId, state, message);
                lastMessage = message;
            }

            if ("done".equals(state)) {
                JsonNode result = status.get("result");
                if (result == null || result.isNull()) {
                    log.warn("JobScrapingScheduler: Scrape job {} finished without a payload", jobId);
                    return null;
                }
                return objectMapper.convertValue(result, ScraperResponse.class);
            }
            if ("error".equals(state)) {
                // Thrown, not returned: a manual caller must not read a failed scrape as
                // "completed, zero jobs".
                throw new IllegalStateException(
                        source + " scrape failed: " + status.path("error").asText("unknown error"));
            }
        }

        throw new IllegalStateException(source + " scrape did not finish within " + budget
                + ". It may still be running on the AI service — check its logs, or lower SCRAPER_LIMIT.");
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
                recruitment.setDedupKey(rDto.getDedupKey());
                // Labelled on the way in rather than by a later sweep: a posting that
                // arrives unclassified is invisible to every level filter until the
                // next backfill runs, which is a silent gap rather than a visible one.
                recruitment.setSeniority(seniorityClassifier.classify(
                        str(rDto.getRecruitmentInfos(), "title"),
                        str(rDto.getRecruitmentInfos(), "experience")).name());
                recruitment.setClassifiedAt(java.time.LocalDateTime.now());

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

    /** Reads a string out of a scraped jsonb map, tolerating a missing key. */
    private static String str(java.util.Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? "" : value.toString();
    }
}
