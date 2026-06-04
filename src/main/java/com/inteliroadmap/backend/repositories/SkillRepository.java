package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SkillRepository extends JpaRepository<Skill, UUID> {
    List<Skill> findBySkillNameContainingIgnoreCaseOrCareerContainingIgnoreCase(String skillName, String career);
    Skill findBySkillName(String skillName);
}
