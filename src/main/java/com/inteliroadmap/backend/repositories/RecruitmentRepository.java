package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.Recruitment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecruitmentRepository extends JpaRepository<Recruitment, String> {
    boolean existsByTopCvRecruitmentId(String topCvRecruitmentId);
    Recruitment findByTopCvRecruitmentId(String topCvRecruitmentId);
}
