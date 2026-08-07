package com.inteliroadmap.backend.domain.dto.response.student;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** One skill the student is asked about. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentQuestionResponse {

    private UUID skillId;
    private String skillName;
    private String category;

    /** HIGH | AVG | LOW — how much the target career needs this skill. */
    private String importance;

    /**
     * Whether an APPLIED or PROFESSIONAL answer must carry a written note.
     *
     * <p>Set on the few highest-importance skills only. Asking for prose on all
     * fifteen would make the form long enough that students stop writing
     * anything useful, and the AI only needs a handful of real claims to tell a
     * shipped project from a followed tutorial.
     */
    private boolean noteRequired;
}
