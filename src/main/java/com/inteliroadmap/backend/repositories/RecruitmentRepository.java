package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.Recruitment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecruitmentRepository extends JpaRepository<Recruitment, String> {
    boolean existsByTopCvRecruitmentId(String topCvRecruitmentId);
    Recruitment findByTopCvRecruitmentId(String topCvRecruitmentId);
    
    java.util.List<Recruitment> findTop10ByTitleContainingIgnoreCase(String keyword);

    @org.springframework.data.jpa.repository.Query("SELECT r.salary FROM Recruitment r WHERE r.salary IS NOT NULL AND r.salary != ''")
    java.util.List<String> findAllSalaries();
}
