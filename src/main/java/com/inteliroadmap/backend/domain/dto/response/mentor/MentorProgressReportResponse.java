package com.inteliroadmap.backend.domain.dto.response.mentor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentorProgressReportResponse {
    private List<Metric> metrics;
    private List<StudentProgress> studentsProgress;
    private List<NeedsAttention> needsAttention;
    private List<SkillGap> skillGaps;

}
