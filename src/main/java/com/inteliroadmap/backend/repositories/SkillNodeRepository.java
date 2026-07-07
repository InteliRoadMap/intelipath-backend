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
    List<SkillNode> findByCareerRole_CareerId(UUID careerId);
    List<SkillNode> findByCareerRole_CareerIdOrderByNodeLevelAscNodeNameAsc(UUID careerId);
    List<SkillNode> findBySkill_SkillIdAndCareerRole_CareerId(UUID skillId, UUID careerId);
    boolean existsByParentNode_NodeId(UUID nodeId);
    boolean existsByPreviousNode_NodeId(UUID nodeId);

    @Query("SELECT COUNT(sn) FROM SkillNode sn WHERE sn.careerRole.careerId = :careerId")
    int findTotalNodeOfRoadmap(@Param("careerId") UUID careerId);

    // Number of distinct careers that already have at least one roadmap node.
    @Query("SELECT COUNT(DISTINCT sn.careerRole.careerId) FROM SkillNode sn")
    long countDistinctCareersWithNodes();

}
