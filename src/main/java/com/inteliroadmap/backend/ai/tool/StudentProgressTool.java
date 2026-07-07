package com.inteliroadmap.backend.ai.tool;

import com.inteliroadmap.backend.domain.dto.response.student.DashboardRoadmapProgressResponse;
import com.inteliroadmap.backend.domain.dto.response.student.SkillGapItemResponse;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.services.StudentDashboardService;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service("studentProgressTool")
@Description("Check the exact learning roadmap progress and skill gaps of the current student to provide personalized advice. You must provide the student's User ID.")
public class StudentProgressTool implements Function<StudentProgressTool.Request, StudentProgressTool.Response> {

    private final StudentDashboardService studentDashboardService;
    private final StudentRepository studentRepository;

    public StudentProgressTool(StudentDashboardService studentDashboardService, StudentRepository studentRepository) {
        this.studentDashboardService = studentDashboardService;
        this.studentRepository = studentRepository;
    }

    public record Request(UUID userId) {}

    public record Response(String roadmapStatus, List<String> missingSkills) {}

    @Override
    public Response apply(Request request) {
        try {
            if (request.userId() == null) {
                return new Response("[TOOL_ERROR] Missing userId in request. The AI must extract the User ID from its system prompt context.", List.of());
            }

            Student student = studentRepository.findById(request.userId()).orElse(null);
            if (student == null) {
                return new Response("[TOOL_ERROR] Student profile not found for the given User ID.", List.of());
            }

            DashboardRoadmapProgressResponse progress = studentDashboardService.getRoadmapProgress(student);
            List<SkillGapItemResponse> gaps = studentDashboardService.getSkillGaps(student);

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
            return new Response("[TOOL_ERROR] Error fetching progress: " + e.getMessage(), List.of());
        }
    }
}
