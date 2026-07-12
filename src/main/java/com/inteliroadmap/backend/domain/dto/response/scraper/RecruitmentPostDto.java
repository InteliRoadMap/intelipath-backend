package com.inteliroadmap.backend.domain.dto.response.scraper;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecruitmentPostDto {

    @JsonProperty("post_id")
    private UUID postId;

    private CompanyDto company;
    private RecruitmentDto recruitment;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyDto {
        private String name;
        private String logo;
        @JsonProperty("company_link")
        private String companyLink;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecruitmentDto {
        private String title;
        private String salary;
        private String location;
        private String experience;
        @JsonProperty("application_deadline")
        private LocalDate applicationDeadline;
        private List<String> tags;
    }
}
