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
    
    @Query(value = "SELECT * FROM recruitments WHERE recruitment_infos->>'title' ILIKE %:keyword% LIMIT 10", nativeQuery = true)
    List<Recruitment> findTop10ByTitleContainingIgnoreCase(@Param("keyword") String keyword);

    @Query(value = "SELECT recruitment_infos->>'salary' FROM recruitments WHERE recruitment_infos->>'salary' IS NOT NULL AND recruitment_infos->>'salary' != ''", nativeQuery = true)
    List<String> findAllSalaries();
}
