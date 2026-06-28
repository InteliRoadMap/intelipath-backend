package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.SkillNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SkillNodeRepository extends JpaRepository<SkillNode, UUID> {
    SkillNode findByNodeId(UUID nodeId);
    SkillNode findByNodeName(String nodeName);
    List<SkillNode> findByCareerId(UUID careerId);
    List<SkillNode> findByCareerIdOrderByNodeLevelAscNodeNameAsc(UUID careerId);
    List<SkillNode> findBySkillIdAndCareerId(UUID skillId, UUID careerId);

    @Query("SELECT COUNT(sn) FROM SkillNode sn WHERE sn.careerId = :careerId")
    int findTotalNodeOfRoadmap(@Param("careerId") UUID careerId);

}
