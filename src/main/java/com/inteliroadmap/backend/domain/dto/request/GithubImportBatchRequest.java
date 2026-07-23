package com.inteliroadmap.backend.domain.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Request to import several selected GitHub repositories at once from the Sync-GitHub picker.
 * Each entry is a full repository URL; the server runs AI analysis per repo and returns the
 * resulting (unsaved) project entries for the student to add to their portfolio.
 */
@Data
public class GithubImportBatchRequest {

    @NotEmpty(message = "Select at least one repository to import")
    @Size(max = 20, message = "You can import at most 20 repositories at once")
    private List<String> repoUrls;
}
