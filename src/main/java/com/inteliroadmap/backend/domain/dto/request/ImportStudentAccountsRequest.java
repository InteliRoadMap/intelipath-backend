package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
public class ImportStudentAccountsRequest {
    @NotEmpty
    private List<StudentAccounts> accounts;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StudentAccounts{
        @NotNull private String fullName;
        @NotNull private String email;
        @NotNull private LocalDate admissionDate;
        @NotNull private String major;
        @NotNull private String curriculum;
    }
}
