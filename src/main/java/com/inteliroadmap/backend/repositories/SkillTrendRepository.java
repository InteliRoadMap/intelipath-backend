package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.SkillTrend;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SkillTrendRepository extends JpaRepository<SkillTrend, UUID> {
    List<SkillTrend> findBySkill_SkillIdInOrderByWeekStampAsc(List<UUID> skillIds);

    /**
     * Trend rows inside the demand window.
     *
     * <p>Replaces a {@code findAll()} that pulled the whole table and filtered it
     * in Java on every roadmap request. {@code idx_skill_trends_week_stamp} backs
     * the predicate.
     */
    List<SkillTrend> findByWeekStampGreaterThanEqual(LocalDate from);

    /**
     * Clears the trends the current run is about to rebuild.
     *
     * <p>Scoped rather than {@code deleteAll}: a run only looks at postings inside
     * the window, so wiping everything would delete history it has no data to
     * replace.
     */
    @Transactional
    @Modifying
    void deleteByWeekStampGreaterThanEqual(LocalDate from);
}
