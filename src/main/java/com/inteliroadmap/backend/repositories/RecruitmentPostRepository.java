package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.Company;
import com.inteliroadmap.backend.domain.entity.Recruitment;
import com.inteliroadmap.backend.domain.entity.RecruitmentPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface RecruitmentPostRepository extends JpaRepository<RecruitmentPost, UUID> {
    RecruitmentPost findFirstByCompany_TopCvCompanyIdAndRecruitment_TopCvRecruitmentId(String companyId, String recruitmentId);

    /** Removes posts whose application/expiry date is strictly before the given date. */
    long deleteByExpiredAtBefore(LocalDate date);
}
