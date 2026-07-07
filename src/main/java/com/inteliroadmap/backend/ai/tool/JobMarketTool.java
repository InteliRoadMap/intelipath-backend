package com.inteliroadmap.backend.ai.tool;

import com.inteliroadmap.backend.domain.entity.Recruitment;
import com.inteliroadmap.backend.repositories.RecruitmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        List<Recruitment> recruitments = searchJobs(request.keyword());

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

    /**
     * Search job titles for a keyword. First tries the whole phrase; if that
     * finds nothing (e.g. "Tester QC" when the DB has "Manual Tester"), falls
     * back to matching individual words and ranks results by how many words hit,
     * so near-synonym queries still return the closest jobs.
     */
    private List<Recruitment> searchJobs(String keyword) {
        String phrase = keyword == null ? "" : keyword.trim();
        if (phrase.isEmpty()) {
            return List.of();
        }

        List<Recruitment> exact = recruitmentRepository.findTop10ByTitleContainingIgnoreCase(phrase);
        if (!exact.isEmpty()) {
            return exact;
        }

        // Token fallback: match any meaningful word, rank by number of words matched.
        Map<String, Recruitment> byId = new LinkedHashMap<>();
        Map<String, Integer> score = new HashMap<>();
        for (String token : phrase.split("\\s+")) {
            if (token.length() < 2) {
                continue; // skip noise like "QC" is length 2 -> kept; single chars dropped
            }
            for (Recruitment r : recruitmentRepository.findTop10ByTitleContainingIgnoreCase(token)) {
                String id = r.getTopCvRecruitmentId();
                byId.putIfAbsent(id, r);
                score.merge(id, 1, Integer::sum);
            }
        }

        return byId.values().stream()
                .sorted((a, b) -> score.get(b.getTopCvRecruitmentId()) - score.get(a.getTopCvRecruitmentId()))
                .limit(10)
                .collect(Collectors.toList());
    }
}
