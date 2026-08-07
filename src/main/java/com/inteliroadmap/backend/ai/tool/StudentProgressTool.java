package com.inteliroadmap.backend.ai.tool;

import com.inteliroadmap.backend.domain.dto.response.student.DashboardRoadmapProgressResponse;
import com.inteliroadmap.backend.domain.dto.response.student.SkillGapItemResponse;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.repositories.StudentNodeSelectionRepository;
import com.inteliroadmap.backend.repositories.SkillNodeRepository;
import com.inteliroadmap.backend.services.StudentDashboardService;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service("studentProgressTool")
@Description("Check the exact learning roadmap progress and skill gaps of the current student to provide personalized advice. You must provide the student's User ID.")
public class StudentProgressTool implements Function<StudentProgressTool.Request, StudentProgressTool.Response> {

    private final StudentDashboardService studentDashboardService;
    private final StudentRepository studentRepository;
    private final StudentNodeSelectionRepository selectionRepository;
    private final SkillNodeRepository skillNodeRepository;

    public StudentProgressTool(StudentDashboardService studentDashboardService, StudentRepository studentRepository,
                               StudentNodeSelectionRepository selectionRepository,
                               SkillNodeRepository skillNodeRepository) {
        this.studentDashboardService = studentDashboardService;
        this.studentRepository = studentRepository;
        this.selectionRepository = selectionRepository;
        this.skillNodeRepository = skillNodeRepository;
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

            // Career requirements contain every technology alternative. Once a
            // student picks Java, sibling choices such as Python and Node.js are
            // not personal gaps and must never be presented as mandatory work.
            Set<String> unselectedAlternatives = new HashSet<>();
            selectionRepository.findByStudent_UserId(student.getUserId()).forEach(selection ->
                    skillNodeRepository.findByParentNode_NodeId(selection.getGroupNode().getNodeId()).stream()
                            .filter(node -> !node.getNodeId().equals(selection.getChosenNode().getNodeId()))
                            .forEach(node -> unselectedAlternatives.add(node.getNodeName().toLowerCase())));

            List<String> missingSkillNames = gaps.stream()
                    .filter(gap -> gap.getTitle() == null
                            || !unselectedAlternatives.contains(gap.getTitle().toLowerCase()))
                    .map(gap -> gap.getTitle() + " (Severity: " + gap.getSeverity() + ")")
                    .toList();

            return new Response(progressText, missingSkillNames);
        } catch (Exception e) {
            return new Response("[TOOL_ERROR] Error fetching progress: " + e.getMessage(), List.of());
        }
    }
}
