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
public class Recruitment {
    private String title;
    private String salary;
    private String location;
    private String experience;
    private String seniority;
    @JsonProperty("application_deadline")
    private LocalDate applicationDeadline;
    private List<String> tags;
}
