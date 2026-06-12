package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.AcademicCounselor;
import com.inteliroadmap.backend.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AcademicCounselorRepository extends JpaRepository<AcademicCounselor, UUID> {
}
