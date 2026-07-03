package com.inteliroadmap.backend.scheduler;

import com.inteliroadmap.backend.services.SkillExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SkillExtractionScheduler {

    private final SkillExtractionService skillExtractionService;

    // Run at 2 AM every day
    @Scheduled(cron = "0 0 2 * * ?")
    public void runSkillExtractionJob() {
        log.info("SkillExtractionScheduler: CronJob Started: Beginning scheduled skill extraction at 2 AM.");
        try {
            skillExtractionService.extractAndRebuildSkillTrends();
            log.info("SkillExtractionScheduler: CronJob Finished: Skill extraction completed successfully.");
        } catch (Exception e) {
            log.error("SkillExtractionScheduler: CronJob Failed: Error during skill extraction.", e);
        }
    }
}
