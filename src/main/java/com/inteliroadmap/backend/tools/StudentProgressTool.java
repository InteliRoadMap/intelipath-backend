package com.inteliroadmap.backend.tools;

import com.inteliroadmap.backend.domain.dto.response.student.DashboardRoadmapProgressResponse;
import com.inteliroadmap.backend.domain.dto.response.student.SkillGapItemResponse;
import com.inteliroadmap.backend.services.StudentDashboardService;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service("studentProgressTool")
@Description("Check the exact learning roadmap progress and skill gaps of the current student to provide personalized advice.")
public class StudentProgressTool implements Function<StudentProgressTool.Request, StudentProgressTool.Response> {

    private final StudentDashboardService studentDashboardService;

    public StudentProgressTool(StudentDashboardService studentDashboardService) {
        this.studentDashboardService = studentDashboardService;
    }

    public record Request() {}

    public record Response(String roadmapStatus, List<String> missingSkills) {}

    @Override
    public Response apply(Request request) {
        try {
            DashboardRoadmapProgressResponse progress = studentDashboardService.getRoadmapProgress();
            List<SkillGapItemResponse> gaps = studentDashboardService.getSkillGaps();

            String progressText = "Roadmap Status:\n";
            if (progress.getSteps() != null) {
                progressText += progress.getSteps().stream()
                        .map(step -> "- " + step.getTitle() + ": " + step.getStatus())
                        .collect(Collectors.joining("\n"));
            } else {
                progressText += "No roadmap assigned.";
            }

            List<String> missingSkillNames = gaps.stream()
                    .map(gap -> gap.getTitle() + " (Severity: " + gap.getSeverity() + ")")
                    .toList();

            return new Response(progressText, missingSkillNames);
        } catch (Exception e) {
            return new Response("Error fetching progress: " + e.getMessage(), List.of());
        }
    }
}
