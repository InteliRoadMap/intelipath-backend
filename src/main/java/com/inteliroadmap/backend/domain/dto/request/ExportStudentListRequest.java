package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ExportStudentListRequest {
    @NotEmpty(message = "Student ID list is required")
    private List<UUID> studentIds;
}
