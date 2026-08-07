package com.inteliroadmap.backend.domain.dto.response.student;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * How close one career sits to the student's declared skills.
 *
 * <p>A suggestion, never a decision: nothing on this path writes
 * {@code students.career_id}. The counts travel with the score on purpose — a
 * student can argue with "you have 9 of Backend's 29 essential skills" and
 * cannot argue with a bare 0.73.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareerAffinityResponse {

    private UUID careerId;
    private String careerName;

    /** 0 = identical skill sets, 1 = nothing in common. Lower is closer. */
    private Double jaccardDistance;

    /** Essential skills of this career the student already has. */
    private Integer matched;

    /** Size of the career's essential set — the same denominator the level uses. */
    private Integer required;

    /** A few of the matches by name, so the count can be checked rather than trusted. */
    private List<String> topMatchingSkills;

    /** True for the career the student has already chosen, so the UI need not re-derive it. */
    private Boolean current;
}
