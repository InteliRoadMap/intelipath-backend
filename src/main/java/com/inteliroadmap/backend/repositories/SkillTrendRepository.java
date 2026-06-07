package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.SkillTrend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SkillTrendRepository extends JpaRepository<SkillTrend, UUID> {
    List<SkillTrend> findBySkill_SkillIdInOrderByWeekStackAsc(List<UUID> skillIds);
}
