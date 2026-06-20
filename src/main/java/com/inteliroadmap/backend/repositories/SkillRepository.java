package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SkillRepository extends JpaRepository<Skill, UUID> {
    List<Skill> findBySkillNameContainingIgnoreCaseOrCareerContainingIgnoreCase(String skillName, String career);
    List<Skill> findBySkillNameContainingIgnoreCase(String skillName);

    Skill findBySkillName(String skillName);
    Skill findBySkillId(UUID skillId);
}
