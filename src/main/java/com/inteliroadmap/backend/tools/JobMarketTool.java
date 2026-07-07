package com.inteliroadmap.backend.tools;

import com.inteliroadmap.backend.domain.entity.Recruitment;
import com.inteliroadmap.backend.repositories.RecruitmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service("jobMarketTool")
@Description("Search for real-time IT jobs in Vietnam by keyword (e.g. Java, React, Data Engineer) to get salary ranges and requirements.")
@RequiredArgsConstructor
public class JobMarketTool implements Function<JobMarketTool.Request, JobMarketTool.Response> {

    private final RecruitmentRepository recruitmentRepository;

    public record Request(String keyword) {}
    public record Response(List<JobData> jobs, String summary) {}
    public record JobData(String title, String salary, String location, String experience, String url) {}

    @Override
    public Response apply(Request request) {
        log.info("JobMarketTool: AI Called JobMarketTool to search for: {}", request.keyword());
        List<Recruitment> recruitments = recruitmentRepository.findTop10ByTitleContainingIgnoreCase(request.keyword());
        
        if (recruitments.isEmpty()) {
            return new Response(List.of(), "No jobs found for keyword: " + request.keyword());
        }

        List<JobData> jobs = recruitments.stream()
                .map(r -> new JobData(
                        com.inteliroadmap.backend.mappers.ScraperMapper.str(r.getRecruitmentInfos(), "title"),
                        com.inteliroadmap.backend.mappers.ScraperMapper.str(r.getRecruitmentInfos(), "salary"),
                        com.inteliroadmap.backend.mappers.ScraperMapper.str(r.getRecruitmentInfos(), "location"),
                        com.inteliroadmap.backend.mappers.ScraperMapper.str(r.getRecruitmentInfos(), "experience"),
                        com.inteliroadmap.backend.mappers.ScraperMapper.str(r.getRecruitmentInfos(), "link")
                ))
                .collect(Collectors.toList());

        String summary = String.format("Found %d jobs for %s.", jobs.size(), request.keyword());
        return new Response(jobs, summary);
    }
}
