package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.response.market.MarketTrendResponse;
import com.inteliroadmap.backend.domain.dto.response.market.SkillPostingsResponse;

import java.util.List;
import java.util.UUID;

/**
 * Market analytics.
 *
 * <p>Every figure here answers "what does the market want", so it needs a period
 * attached: an all-time count answers "what did the market ever want", which for a
 * student choosing what to learn next is the wrong question. The windowed methods
 * take {@code windowDays}; the original no-argument forms are kept unchanged for
 * callers that genuinely want the whole history.
 */
public interface MarketTrendService {

    /** Default window. A month is long enough to smooth a quiet week, short enough to still be news. */
    int DEFAULT_WINDOW_DAYS = 30;

    List<MarketTrendResponse.CompanyTrendResponse> getTopHiringCompanies(int limit) ;

    List<MarketTrendResponse.SkillTrendResponse> getSkillTrends() ;

    List<MarketTrendResponse.SalaryTrendResponse> getSalaryDistribution() ;

    /** As above, restricted to postings from the last {@code windowDays} days. */
    List<MarketTrendResponse.CompanyTrendResponse> getTopHiringCompanies(int limit, int windowDays) ;

    List<MarketTrendResponse.SkillTrendResponse> getSkillTrends(int windowDays) ;

    List<MarketTrendResponse.SalaryTrendResponse> getSalaryDistribution(int windowDays) ;

    /**
     * How many jobs appeared in the window that had never been advertised before,
     * alongside the total in that window. Lets the UI say "12 new of 48" instead of
     * calling every re-post new.
     */
    MarketTrendResponse.FreshnessResponse getFreshness(int windowDays) ;

    /**
     * Labels postings that carry no seniority yet and returns how many were
     * updated. Idempotent: rows already classified are left alone, so it can be
     * re-run safely after a rule change by clearing the column first.
     */
    int classifyUnlabelledRecruitments() ;

    /**
     * The postings behind a skill's market number.
     *
     * <p>Turns "158 postings" from a claim into something the student can open and
     * check against the original ads. Empty rather than absent when nothing is on
     * file: "we found none" is an answer, and it is a different answer from an
     * error.
     *
     * @param skillId the catalog skill the roadmap node maps to
     * @param limit   how many to return, newest first
     */
    SkillPostingsResponse getPostingsForSkill(UUID skillId, int limit) ;
}
