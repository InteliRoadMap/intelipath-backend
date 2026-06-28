package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.Recruitment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecruitmentRepository extends JpaRepository<Recruitment, String> {
    boolean existsByTopCvRecruitmentId(String topCvRecruitmentId);
    Recruitment findByTopCvRecruitmentId(String topCvRecruitmentId);
    
    @Query(value = "SELECT * FROM processed_recruitments WHERE basic_info->>'title' ILIKE %:keyword% LIMIT 10", nativeQuery = true)
    List<Recruitment> findTop10ByTitleContainingIgnoreCase(@Param("keyword") String keyword);

    @Query(value = "SELECT basic_info->>'salary' FROM processed_recruitments WHERE basic_info->>'salary' IS NOT NULL AND basic_info->>'salary' != ''", nativeQuery = true)
    List<String> findAllSalaries();
}
