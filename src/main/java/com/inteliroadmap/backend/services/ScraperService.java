package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.scraper.CompanyResponse;
import com.inteliroadmap.backend.domain.dto.response.scraper.RecruitmentPostResponse;
import com.inteliroadmap.backend.domain.dto.response.scraper.RecruitmentResponse;

import java.util.List;

public interface ScraperService {

    List<RecruitmentPostResponse> getRecruitmentPosts();

    /**
     * Postings at {@code seniority}, newest first.
     *
     * <p>Postings whose level could not be read are kept in the result. They are
     * jobs the student may well qualify for, and silently dropping them would make
     * the market look smaller than it is — the caller labels them instead.
     *
     * @param seniority FRESHER | JUNIOR | MID | SENIOR, or null for no filter
     */
    List<RecruitmentPostResponse> getRecruitmentPosts(String seniority);

    List<RecruitmentPostResponse> getRecruitmentPosts(String seniority, java.util.UUID careerId);

    CompanyResponse getCompanyInfos(String companyId);

    RecruitmentResponse getRecruitmentInfos(String recruitmentId);
}
