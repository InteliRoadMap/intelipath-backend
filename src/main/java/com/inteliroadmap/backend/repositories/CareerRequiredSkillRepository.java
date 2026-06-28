package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.CareerRequiredSkill;
import com.inteliroadmap.backend.domain.entity.CareerRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CareerRequiredSkillRepository extends JpaRepository<CareerRequiredSkill, UUID> {

    List<CareerRequiredSkill> findByCareerRoleId(UUID careerRoleId);
}
