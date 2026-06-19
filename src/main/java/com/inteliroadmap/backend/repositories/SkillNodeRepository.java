package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.SkillNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SkillNodeRepository extends JpaRepository<SkillNode, UUID> {
    SkillNode findByNodeName(String nodeName);
    List<SkillNode> findByCareerRole_CareerId(UUID careerId);
    List<SkillNode> findByCareerRole_CareerIdOrderByLevelAscNodeNameAsc(UUID careerId);
    List<SkillNode> findBySkillIdAndCareerRole_CareerId(UUID skillId, UUID careerId);
}
