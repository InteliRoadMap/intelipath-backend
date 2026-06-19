package com.inteliroadmap.backend.jobs;

import com.inteliroadmap.backend.parsers.LinkedInParser;
import com.inteliroadmap.backend.parsers.TopCvParser;
import com.inteliroadmap.backend.services.ScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyScrapeJob {

    private final TopCvParser topCvParser;
    private final LinkedInParser linkedInParser;
    private final ScraperService scraperService;

    // Runs every Monday at 9:00 AM // Scraping Recruitment Posts
    @Scheduled(cron = "0 0 9 * * Mon")
    public void scrapeJobs() {
        log.info("Running scheduled scraper job...");
        topCvParser.parseTopCvJobs();
//        linkedInParser.parseLinkedInJobs();
    }

    // Runs every day at 8:00 AM // Remove Overdue Recruitment Posts
    @Scheduled(cron = "0 0 8 * * *")
    public void removeOverdueRecruitmentPosts() {
        log.info("Checking Overdue Recruitment Posts...");
        scraperService.removeOverdueRecruitmentPosts();
    }

//    @EventListener(ApplicationReadyEvent.class)
//    public void runOnStartup() {
//        topCvParser.parseTopCvJobs();
//    }
}
