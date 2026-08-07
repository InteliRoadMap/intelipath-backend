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

}
