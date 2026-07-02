package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.StudentSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserSkillRepository extends JpaRepository<StudentSkill, UUID> {
}
