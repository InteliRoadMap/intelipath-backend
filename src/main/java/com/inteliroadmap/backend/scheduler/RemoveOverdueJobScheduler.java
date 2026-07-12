package com.inteliroadmap.backend.scheduler;

import com.inteliroadmap.backend.repositories.RecruitmentPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class RemoveOverdueJobScheduler {

    private final RecruitmentPostRepository recruitmentPostRepository;

    // Runs every day at 08:00 — deletes recruitment posts whose deadline has passed.
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void removeOverdueRecruitmentPosts() {
        log.info("RemoveOverdueJobScheduler: Removing overdue recruitment posts...");
        long removed = recruitmentPostRepository.deleteByExpiredAtBefore(LocalDate.now());
        log.info("RemoveOverdueJobScheduler: Removed {} overdue recruitment posts.", removed);
    }
}
