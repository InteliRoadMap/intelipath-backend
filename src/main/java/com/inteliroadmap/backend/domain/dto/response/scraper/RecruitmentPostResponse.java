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
public class RecruitmentPostResponse {

    @JsonProperty("post_id")
    private UUID postId;

    private Company company;
    private Recruitment recruitment;

}
