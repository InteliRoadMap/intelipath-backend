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
public class MentorProgressReportDto {
    private List<Metric> metrics;
    private List<StudentProgress> studentsProgress;
    private List<NeedsAttention> needsAttention;
    private List<SkillGap> skillGaps;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Metric {
        private String label;
        private String value;
        private String color;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StudentProgress {
        private String name;
        private String role;
        private int completed;
        private int total;
        private int progress;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NeedsAttention {
        private String name;
        private String issue;
        private String days;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SkillGap {
        private String skill;
        private int count;
        private String priority;
    }
}
