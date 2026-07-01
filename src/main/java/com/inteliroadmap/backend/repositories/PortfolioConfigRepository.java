package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.PortfolioConfig;
import com.inteliroadmap.backend.domain.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PortfolioConfigRepository extends JpaRepository<PortfolioConfig, UUID> {
    PortfolioConfig findByUser_UserId(UUID userId);
}
