package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
import java.util.List;

@Repository
public interface CompanyRepository extends JpaRepository<Company, String> {
    boolean existsByTopCvCompanyId(String topCvCompanyId);
    Company findByTopCvCompanyId(String topCvCompanyId);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM Company c ORDER BY SIZE(c.recruitmentPosts) DESC")
    List<Company> findTopHiringCompanies(Pageable pageable);
}
