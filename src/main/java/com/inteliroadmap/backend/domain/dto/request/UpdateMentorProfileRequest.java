package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMentorProfileRequest {
    @Size(max = 200, message = "Company must not exceed 200 characters")
    private String company;

    @Size(max = 200, message = "Industry focus must not exceed 200 characters")
    private String industryFocus;
}
