package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompanyRepository extends JpaRepository<Company, String> {
    Company findByTopCvCompanyId(String topCvCompanyId);

    /**
     * Top hiring companies with their post count. Returns rows of
     * [Company, Long postCount], ordered by post count descending.
     */
    @Query("SELECT c, COUNT(rp.postId) FROM Company c LEFT JOIN RecruitmentPost rp ON c.topCvCompanyId = rp.company.topCvCompanyId GROUP BY c ORDER BY COUNT(rp.postId) DESC")
    List<Object[]> findTopHiringCompaniesWithCount(org.springframework.data.domain.Pageable pageable);
}
