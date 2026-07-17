package com.inteliroadmap.backend.domain.dto.response.scraper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecruitmentResponse {
    private String topCvRecruitmentId;
    private String recruitmentLink;
    private String title;
    private String salary;
    private String location;
    private String experience;
    private LocalDate applicationDeadline;
    private Object tags;
    private Object descriptions;
    private Object generalInfos;
    private Object relatedTags;
}
