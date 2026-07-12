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
public class VirtualMentorSessionRequest {
    @Size(max = 200, message = "Session name must not exceed 200 characters")
    private String sessionName;
}
