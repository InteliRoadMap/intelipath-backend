package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface CareerRequiredSkillRepository extends JpaRepository<CareerRequiredSkill, UUID> {
    List<CareerRequiredSkill> findByCareerRole_CareerId(UUID careerId);

    boolean existsByCareerRole_CareerIdAndSkill_SkillId(UUID careerId, UUID skillId);
}
