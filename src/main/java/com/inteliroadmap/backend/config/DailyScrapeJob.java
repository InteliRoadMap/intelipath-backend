package com.inteliroadmap.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyScrapeJob {
//    private final ScraperService scraperService;

    // Runs every day at 8:00 AM // Remove Overdue Recruitment Posts
    @Scheduled(cron = "0 0 8 * * *")
    public void removeOverdueRecruitmentPosts() {
        log.info("Checking Overdue Recruitment Posts...");

    }
}
