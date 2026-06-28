package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.PortfolioProject;
import com.inteliroadmap.backend.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PortfolioProjectRepository extends JpaRepository<PortfolioProject, UUID> {
    List<PortfolioProject> findByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}
