package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.RecruitmentSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface RecruitmentSkillRepository extends JpaRepository<RecruitmentSkill, UUID> {

    /**
     * Clears the rows for the postings an extraction is about to rewrite.
     *
     * <p>Scoped by posting date the same way {@code skill_trends} is, so a run that
     * only looked at the last 90 days cannot silently delete what it never examined.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            DELETE FROM recruitment_skills rs
            USING recruitments r
            WHERE r.recruitment_id = rs.recruitment_id
              AND r.posted_date >= :from
            """, nativeQuery = true)
    int deleteForPostingsSince(@Param("from") LocalDate from);

    /**
     * How many distinct postings of each career mention each skill.
     *
     * <p>Counts postings, not mentions: a description that says "Java" six times is one
     * employer asking for Java once. Postings whose career could not be determined are
     * excluded rather than spread across careers — see {@code RecruitmentCareerClassifier}.
     *
     * @return rows of [careerId, skillId, skillName, postingCount]
     */
    @Query(value = """
            SELECT r.career_id, s.skill_id, s.skill_name, count(DISTINCT rs.recruitment_id) AS postings
            FROM recruitment_skills rs
            JOIN recruitments r ON r.recruitment_id = rs.recruitment_id
            JOIN skills s ON s.skill_id = rs.skill_id
            WHERE r.career_id IS NOT NULL
            GROUP BY r.career_id, s.skill_id, s.skill_name
            ORDER BY r.career_id, postings DESC
            """, nativeQuery = true)
    List<Object[]> demandByCareer();

    /** [skillId, distinct posting count] within one career and one time window. */
    @Query(value = """
            SELECT rs.skill_id, count(DISTINCT rs.recruitment_id) AS postings
            FROM recruitment_skills rs
            JOIN recruitments r ON r.recruitment_id = rs.recruitment_id
            WHERE r.career_id = :careerId AND r.posted_date >= :from
            GROUP BY rs.skill_id
            """, nativeQuery = true)
    List<Object[]> demandForCareerSince(@Param("careerId") UUID careerId,
                                        @Param("from") LocalDate from);
}
