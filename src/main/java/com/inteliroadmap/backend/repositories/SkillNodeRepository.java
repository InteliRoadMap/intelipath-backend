package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.SkillNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SkillNodeRepository extends JpaRepository<SkillNode, UUID> {
    SkillNode findByName(String name);
    List<SkillNode> findByCareerRole_CareerId(UUID careerId);
}
