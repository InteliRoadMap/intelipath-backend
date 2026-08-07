package com.inteliroadmap.backend.ai.tool;

import com.inteliroadmap.backend.domain.entity.Recruitment;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.repositories.RecruitmentRepository;
import com.inteliroadmap.backend.services.AuthenticatedStudentService;
import com.inteliroadmap.backend.services.StudentLevelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service("jobMarketTool")
@Description("Search real IT jobs that match the authenticated student's selected career and, when assessed, current level.")
@RequiredArgsConstructor
public class JobMarketTool implements Function<JobMarketTool.Request, JobMarketTool.Response> {

    private final RecruitmentRepository recruitmentRepository;
    private final AuthenticatedStudentService authenticatedStudentService;
    private final StudentLevelService studentLevelService;

    public record Request(String keyword) {}
    public record Response(List<JobData> jobs, String summary) {}
    public record JobData(String title, String salary, String location, String experience,
                          String seniority, String url) {}

    @Override
    public Response apply(Request request) {
        Student student = authenticatedStudentService.getRequiredStudent();
        if (student.getCareerRole() == null) {
            return new Response(List.of(),
                    "No jobs were searched because the student has not selected a target career.");
        }
        UUID careerId = student.getCareerRole().getCareerId();
        String careerName = student.getCareerRole().getCareerName();
        String level = studentLevelService.levelOf(student.getUserId())
                .map(value -> normalizeLevel(value.getLevel()))
                .orElse("");
        log.info("JobMarketTool: scoped search keyword={}, career={}, level={}",
                request.keyword(), careerId, level.isBlank() ? "ALL" : level);
        List<Recruitment> recruitments = searchJobs(request.keyword(), careerId, level);

        if (recruitments.isEmpty()) {
            return new Response(List.of(), String.format(
                    "No %s jobs found for %s%s.", careerName, request.keyword(),
                    level.isBlank() ? "" : " at " + level + " level"));
        }

        List<JobData> jobs = recruitments.stream()
                .map(r -> new JobData(
                        com.inteliroadmap.backend.mappers.ScraperMapper.str(r.getRecruitmentInfos(), "title"),
                        com.inteliroadmap.backend.mappers.ScraperMapper.str(r.getRecruitmentInfos(), "salary"),
                        com.inteliroadmap.backend.mappers.ScraperMapper.str(r.getRecruitmentInfos(), "location"),
                        com.inteliroadmap.backend.mappers.ScraperMapper.str(r.getRecruitmentInfos(), "experience"),
                        r.getSeniority(),
                        com.inteliroadmap.backend.mappers.ScraperMapper.str(r.getRecruitmentInfos(), "link")
                ))
                .collect(Collectors.toList());

        String summary = String.format("Found %d %s jobs for %s%s. Results are database-enforced by career%s.",
                jobs.size(), careerName, request.keyword(),
                level.isBlank() ? "" : " at " + level + " level",
                level.isBlank() ? "" : " and level");
        return new Response(jobs, summary);
    }

    /**
     * Search job titles for a keyword. First tries the whole phrase; if that
     * finds nothing (e.g. "Tester QC" when the DB has "Manual Tester"), falls
     * back to matching individual words and ranks results by how many words hit,
     * so near-synonym queries still return the closest jobs.
     */
    private List<Recruitment> searchJobs(String keyword, UUID careerId, String level) {
        String phrase = keyword == null ? "" : keyword.trim();
        if (phrase.isEmpty()) {
            // Blank means "latest jobs in my enforced scope", not global jobs.
            return recruitmentRepository.findScopedJobs("", careerId, level);
        }

        List<Recruitment> exact = recruitmentRepository.findScopedJobs(phrase, careerId, level);
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
            for (Recruitment r : recruitmentRepository.findScopedJobs(token, careerId, level)) {
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

    private String normalizeLevel(String value) {
        if (value == null) return "";
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        // The recruitment classifier uses four employable bands. Keep the
        // student's richer roadmap scale, but map its boundary labels here.
        return switch (normalized) {
            case "BEGINNER" -> "FRESHER";
            case "EXPERT" -> "SENIOR";
            default -> normalized;
        };
    }
}
