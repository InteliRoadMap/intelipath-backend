package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.GithubImportAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GithubImportAuditRepository extends JpaRepository<GithubImportAudit, UUID> {

    /** The audit for one repository. Unique per (user, repo) — re-imports replace it. */
    Optional<GithubImportAudit> findByUserIdAndRepoUrl(UUID userId, String repoUrl);

    List<GithubImportAudit> findByUserIdOrderByAnalyzedAtDesc(UUID userId);
}
