package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.Recruitment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecruitmentRepository extends JpaRepository<Recruitment, String> {
    boolean existsByTopCvRecruitmentId(String topCvRecruitmentId);
    Recruitment findByTopCvRecruitmentId(String topCvRecruitmentId);
    
    @Query(value = "SELECT * FROM recruitments WHERE recruitment_infos->>'title' ILIKE %:keyword% LIMIT 10", nativeQuery = true)
    List<Recruitment> findTop10ByTitleContainingIgnoreCase(@Param("keyword") String keyword);

    @Query(value = "SELECT recruitment_infos->>'salary' FROM recruitments WHERE recruitment_infos->>'salary' IS NOT NULL AND recruitment_infos->>'salary' != ''", nativeQuery = true)
    List<String> findAllSalaries();

    /**
     * The postings that actually mention a skill — the rows behind the count.
     *
     * <p>Every market figure the student is shown is an aggregate: "158 postings",
     * "19% of jobs". An aggregate a reader cannot open is a number they have to
     * take on faith, and this product asks them to choose a career on the strength
     * of those numbers. This returns the evidence itself: title, where, what it
     * pays, and a link out to the posting so the claim can be checked against the
     * source rather than against us.
     *
     * <p>Newest first, because a posting from last week and one from last year are
     * not equal evidence and the student should see which is which. Rows with no
     * date sort last rather than being dropped — they still mention the skill.
     */
    @Query(value = """
            SELECT r.recruitment_id,
                   r.recruitment_infos->>'title'      AS title,
                   r.recruitment_infos->>'location'   AS location,
                   r.recruitment_infos->>'salary'     AS salary,
                   r.recruitment_infos->>'experience' AS experience,
                   r.recruitment_infos->>'link'       AS link,
                   r.posted_date,
                   r.seniority
            FROM recruitments r
            JOIN recruitment_skills rs ON rs.recruitment_id = r.recruitment_id
            WHERE rs.skill_id = :skillId
            ORDER BY r.posted_date DESC NULLS LAST
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findPostingsForSkill(@Param("skillId") UUID skillId, @Param("limit") int limit);

    /** How many there are in total, so the list can say what it is a sample of. */
    @Query(value = "SELECT count(*) FROM recruitment_skills WHERE skill_id = :skillId", nativeQuery = true)
    long countPostingsForSkill(@Param("skillId") UUID skillId);

    /**
     * Postings published on or after {@code from} — the denominator behind every
     * "x% of jobs ask for this" figure, so it has to count the same population the
     * skill trends were extracted from.
     *
     * <p>Rows with no posted_date are excluded rather than counted: including them
     * would inflate the denominator with postings that could not have contributed
     * to a dated trend row, quietly deflating every percentage.
     */
    long countByPostedDateGreaterThanEqual(java.time.LocalDate from);

    /** Career-specific denominator for career demand percentages. */
    @Query(value = """
            SELECT count(DISTINCT recruitment_id)
            FROM recruitments
            WHERE career_id = :careerId AND posted_date >= :from
            """, nativeQuery = true)
    long countCareerPostingsSince(@Param("careerId") UUID careerId,
                                  @Param("from") java.time.LocalDate from);

    /**
     * Postings inside the trend window.
     *
     * <p>Skill extraction used to read the whole table on every run. Trends are
     * only ever queried over a window, so everything older was being re-analysed
     * to produce rows nothing reads — a cost that grows with the archive rather
     * than with the work.
     */
    List<Recruitment> findByPostedDateGreaterThanEqual(java.time.LocalDate from);

    /**
     * Salaries from postings on or after {@code from}, counting each job once.
     *
     * <p>{@code DISTINCT ON (dedup_key)} keeps only the most recent posting of a
     * re-advertised job. Rows scraped before dedup_key existed fall back to their
     * own id, so they are counted individually rather than silently collapsed into
     * one another.
     */
    @Query(value = """
            SELECT recruitment_infos->>'salary'
            FROM (
                SELECT DISTINCT ON (COALESCE(dedup_key, recruitment_id))
                       recruitment_infos, posted_date
                FROM recruitments
                WHERE posted_date >= :from
                ORDER BY COALESCE(dedup_key, recruitment_id), posted_date DESC
            ) latest
            WHERE recruitment_infos->>'salary' IS NOT NULL
              AND recruitment_infos->>'salary' != ''
            """, nativeQuery = true)
    List<String> findSalariesSince(@Param("from") java.time.LocalDate from);

    /**
     * Top hiring companies within a window, by number of distinct jobs.
     *
     * <p>Counts jobs, not postings: a company that re-advertised the same role six
     * times is not hiring six people, and the previous all-time count ranked
     * whoever had posted most since the database was created.
     */
    @Query(value = """
            SELECT rp.company_id, COUNT(DISTINCT COALESCE(r.dedup_key, r.recruitment_id)) AS job_count
            FROM recruitment_posts rp
            JOIN recruitments r ON r.recruitment_id = rp.recruitment_id
            WHERE r.posted_date >= :from
            GROUP BY rp.company_id
            ORDER BY job_count DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findTopHiringCompanyIdsSince(@Param("from") java.time.LocalDate from,
                                                @Param("limit") int limit);

    @Query(value = """
            SELECT rp.company_id, COUNT(DISTINCT COALESCE(r.dedup_key, r.recruitment_id)) AS job_count
            FROM recruitment_posts rp JOIN recruitments r ON r.recruitment_id = rp.recruitment_id
            WHERE r.posted_date >= :from
              AND r.career_id = :careerId
              AND (:seniority = '' OR r.seniority = :seniority)
            GROUP BY rp.company_id ORDER BY job_count DESC LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findTopHiringCompanyIdsScoped(@Param("from") java.time.LocalDate from,
                                                  @Param("careerId") UUID careerId,
                                                  @Param("seniority") String seniority,
                                                  @Param("limit") int limit);

    @Query(value = """
            SELECT recruitment_infos->>'salary'
            FROM (
                SELECT DISTINCT ON (COALESCE(dedup_key, recruitment_id))
                       recruitment_infos, posted_date
                FROM recruitments
                WHERE posted_date >= :from AND career_id = :careerId
                  AND (:seniority = '' OR seniority = :seniority)
                ORDER BY COALESCE(dedup_key, recruitment_id), posted_date DESC
            ) latest
            WHERE recruitment_infos->>'salary' IS NOT NULL AND recruitment_infos->>'salary' != ''
            """, nativeQuery = true)
    List<String> findSalariesScoped(@Param("from") java.time.LocalDate from,
                                    @Param("careerId") UUID careerId,
                                    @Param("seniority") String seniority);

    @Query(value = """
            SELECT s.skill_name, date_trunc('week', r.posted_date)::date AS week,
                   count(DISTINCT rs.recruitment_id) AS jobs
            FROM recruitment_skills rs
            JOIN recruitments r ON r.recruitment_id = rs.recruitment_id
            JOIN skills s ON s.skill_id = rs.skill_id
            WHERE r.posted_date >= :from AND r.career_id = :careerId
              AND (:seniority = '' OR r.seniority = :seniority)
            GROUP BY s.skill_name, week ORDER BY week, jobs DESC
            """, nativeQuery = true)
    List<Object[]> findSkillTrendsScoped(@Param("from") java.time.LocalDate from,
                                         @Param("careerId") UUID careerId,
                                         @Param("seniority") String seniority);

    @Query(value = """
            SELECT COUNT(DISTINCT COALESCE(dedup_key, recruitment_id))
            FROM recruitments WHERE posted_date >= :from AND career_id = :careerId
              AND (:seniority = '' OR seniority = :seniority)
            """, nativeQuery = true)
    long countDistinctJobsScoped(@Param("from") java.time.LocalDate from,
                                 @Param("careerId") UUID careerId,
                                 @Param("seniority") String seniority);

    @Query(value = """
            SELECT COUNT(*) FROM recruitments r
            WHERE r.posted_date >= :from AND r.career_id = :careerId
              AND (:seniority = '' OR r.seniority = :seniority)
              AND NOT EXISTS (SELECT 1 FROM recruitments p WHERE p.dedup_key=r.dedup_key
                  AND p.dedup_key IS NOT NULL AND p.posted_date < r.posted_date)
            """, nativeQuery = true)
    long countGenuinelyNewScoped(@Param("from") java.time.LocalDate from,
                                 @Param("careerId") UUID careerId,
                                 @Param("seniority") String seniority);

    @Query(value = """
            SELECT MAX(posted_date) FROM recruitments
            WHERE career_id = :careerId AND (:seniority = '' OR seniority = :seniority)
            """, nativeQuery = true)
    java.time.LocalDate findLatestPostedDateScoped(@Param("careerId") UUID careerId,
                                                    @Param("seniority") String seniority);

    @Query(value = """
            SELECT * FROM recruitments
            WHERE career_id = :careerId
              AND (:seniority = '' OR seniority = :seniority)
              AND recruitment_infos->>'title' ILIKE concat('%', :keyword, '%')
            ORDER BY posted_date DESC NULLS LAST LIMIT 10
            """, nativeQuery = true)
    List<Recruitment> findScopedJobs(@Param("keyword") String keyword,
                                     @Param("careerId") UUID careerId,
                                     @Param("seniority") String seniority);

    /**
     * Genuinely new postings: on or after {@code from}, and never advertised
     * earlier under the same job identity.
     *
     * <p>This is what makes a "New" badge honest. Without the NOT EXISTS clause a
     * role re-posted every fortnight looks new every fortnight.
     */
    @Query(value = """
            SELECT COUNT(*) FROM recruitments r
            WHERE r.posted_date >= :from
              AND NOT EXISTS (
                  SELECT 1 FROM recruitments p
                  WHERE p.dedup_key = r.dedup_key
                    AND p.dedup_key IS NOT NULL
                    AND p.posted_date < r.posted_date
              )
            """, nativeQuery = true)
    long countGenuinelyNewSince(@Param("from") java.time.LocalDate from);

    /** Distinct jobs — not postings — advertised on or after {@code from}. */
    @Query(value = """
            SELECT COUNT(DISTINCT COALESCE(dedup_key, recruitment_id))
            FROM recruitments
            WHERE posted_date >= :from
            """, nativeQuery = true)
    long countDistinctJobsSince(@Param("from") java.time.LocalDate from);

    /**
     * Most recent posting on file. Surfaced so a stale scrape is visible in the UI
     * instead of being read as "this is the market today".
     */
    @Query(value = "SELECT MAX(posted_date) FROM recruitments", nativeQuery = true)
    java.time.LocalDate findLatestPostedDate();

    /** Postings with no seniority yet — the backfill's working set. */
    List<Recruitment> findBySeniorityIsNull();
}
