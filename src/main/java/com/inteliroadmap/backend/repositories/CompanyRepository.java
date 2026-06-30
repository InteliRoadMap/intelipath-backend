package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompanyRepository extends JpaRepository<Company, String> {
    Company findByTopCvCompanyId(String topCvCompanyId);

    @Query("SELECT c FROM Company c LEFT JOIN RecruitmentPost rp ON c.topCvCompanyId = rp.companyId GROUP BY c ORDER BY COUNT(rp.postId) DESC")
    List<Company> findTopHiringCompanies(org.springframework.data.domain.Pageable pageable);
}
