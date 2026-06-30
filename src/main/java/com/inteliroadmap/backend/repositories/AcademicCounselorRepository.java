package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.AcademicCounselor;
import com.inteliroadmap.backend.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AcademicCounselorRepository extends JpaRepository<AcademicCounselor, UUID> {
    AcademicCounselor findByUserId(UUID id);
}
