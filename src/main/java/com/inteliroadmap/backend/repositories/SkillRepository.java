package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Repository
public interface SkillRepository extends JpaRepository<Skill, UUID> {

    @Query("SELECT s FROM Skill s WHERE upper(s.catalogStatus) = 'ACTIVE' "
            + "AND lower(s.skillName) LIKE lower(concat('%', :skillName, '%'))")
    List<Skill> findBySkillNameContainingIgnoreCase(@org.springframework.data.repository.query.Param("skillName") String skillName);

    Skill findBySkillName(String skillName);

    /**
     * Every catalog row whose name matches case-insensitively.
     *
     * <p>Returns a list rather than one row because the catalog does contain
     * case-duplicates — {@code Pytorch} beside {@code PyTorch} — and a
     * single-result signature turns that into a {@code NonUniqueResultException}
     * that rolls back the whole caller. A skill-extraction run over 866 postings
     * was lost that way. Duplicates are a data problem worth fixing, but the
     * lookup should not be the thing that fails when they exist.
     *
     * <p>Prefer {@link #findOneBySkillNameIgnoreCase} unless the caller genuinely
     * wants to see the duplicates.
     */
    List<Skill> findAllBySkillNameIgnoreCase(String skillName);

    Skill findBySkillId(UUID skillId);

    /**
     * Catalog rows a student could plausibly claim to hold.
     *
     * <p><b>Why the whole catalog is the wrong list.</b> The skill picker asked for
     * {@code findAll()} — 3.895 rows. But {@code skills} is not a list of skills; it is
     * also every node title of every imported roadmap, so the picker offered
     * {@code $elemMatch}, {@code --watch}, {@code @else if} and {@code path module} for a
     * student to declare, sorted before {@code Android}. Those rows are not junk and must
     * not be deleted — they are what the roadmap teaches — they are simply not things a
     * person says they can do.
     *
     * <p><b>Evidence, not a blacklist.</b> A row qualifies when something outside the
     * catalog vouches for it: an employer named it in a posting, or a career grades it
     * HIGH/AVG. Both are recomputed from data on every extraction run
     * ({@code CareerSkillMarketGrader}), so this list follows the market instead of ageing
     * like a hand-written allow-list would. Measured on the live database this returns 720
     * of 3.895 rows, and keeps {@code bcrypt} and {@code OWASP Risks} — real skills with
     * zero Vietnamese postings, held by the grade half of the test.
     *
     * <p>Name-shape rules are applied by the caller through {@code CoreSkillEligibility}
     * rather than being repeated in SQL, so there is one definition of "is this a skill
     * name" and not two that can drift apart.
     */
    @Query(value = """
            SELECT s.* FROM skills s
            WHERE upper(coalesce(s.catalog_status, 'ACTIVE')) = 'ACTIVE'
              AND (EXISTS (SELECT 1 FROM recruitment_skills rs WHERE rs.skill_id = s.skill_id)
               OR EXISTS (SELECT 1 FROM career_required_skills c
                           WHERE c.skill_id = s.skill_id
                             AND upper(coalesce(c.importance_level, '')) IN ('HIGH', 'AVG')))
            ORDER BY s.skill_name
            """, nativeQuery = true)
    List<Skill> findDeclarableCandidates();

    /**
     * One catalog row for this name, or null.
     *
     * <p>When duplicates exist the choice is made by name so it is stable across
     * runs: two runs that disagreed on which row is "the" PyTorch would split one
     * skill's trend history across both.
     */
    default Skill findOneBySkillNameIgnoreCase(String skillName) {
        return findAllBySkillNameIgnoreCase(skillName).stream()
                .min(Comparator.comparing(Skill::getSkillName,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }
}
