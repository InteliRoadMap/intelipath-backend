package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.SkillNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SkillNodeRepository extends JpaRepository<SkillNode, UUID> {
    SkillNode findByNodeId(UUID nodeId);
    SkillNode findByNodeName(String nodeName);
    List<SkillNode> findByCareerRole_CareerId(UUID careerId);
    List<SkillNode> findByCareerRole_CareerIdOrderByNodeLevelAscNodeNameAsc(UUID careerId);

    /**
     * The career's nodes in the order a student would actually walk them, with the
     * parent joined so a caller can name the context a node sits in.
     *
     * The older sibling above orders by {@code nodeLevel}, which reads 0 on 2.610
     * nodes; ties then fall to {@code nodeName}, and {@code $} sorts ahead of every
     * letter in ASCII. That put {@code $all}, {@code $and}, {@code $elemMatch},
     * {@code $eq} — MongoDB query operators four levels deep under "Array
     * Operators" — at the very top of the student's action list, as the first
     * things the roadmap told them to learn.
     */
    @Query("SELECT sn FROM SkillNode sn "
            + "LEFT JOIN FETCH sn.parentNode "
            + "LEFT JOIN FETCH sn.skill "
            + "WHERE sn.careerRole.careerId = :careerId "
            + "ORDER BY sn.depth ASC NULLS LAST, sn.sortOrder ASC NULLS LAST, sn.nodeName ASC")
    List<SkillNode> findOrderedForCareer(UUID careerId);
    // ── FR2.3 publication gate ──────────────────────────────────────────────
    // The finders above return the whole career, draft nodes included, and they
    // stay that way: the editor has to see what it is being asked to fix, the
    // counselor has to see what the student is walking, and the selection and
    // unlock logic reason about the tree's shape rather than about what is on
    // screen. These three are the display path, and they are the only place the
    // gate belongs.
    //
    // Hiding a draft node cannot orphan anything: the gate only ever marks
    // leaves, so a withheld node has no children left dangling above it. That
    // holds by construction of the UPDATE in the init SQL, and is checked against
    // the live table -- 0 draft nodes with children -- rather than assumed.

    /** {@link #findByCareerRole_CareerId} minus drafts. */
    @Query("SELECT sn FROM SkillNode sn "
            + "WHERE sn.careerRole.careerId = :careerId AND (sn.status IS NULL OR sn.status <> 'DRAFT')")
    List<SkillNode> findPublishedForCareer(UUID careerId);

    /** {@link #findByCareerRole_CareerIdOrderByNodeLevelAscNodeNameAsc} minus drafts. */
    @Query("SELECT sn FROM SkillNode sn "
            + "WHERE sn.careerRole.careerId = :careerId AND (sn.status IS NULL OR sn.status <> 'DRAFT') "
            + "ORDER BY sn.nodeLevel ASC, sn.nodeName ASC")
    List<SkillNode> findPublishedForCareerLegacyOrder(UUID careerId);

    /**
     * {@link #findOrderedForCareer} minus drafts.
     *
     * <p><b>{@code skill} is fetched, not lazily resolved.</b> Every node on the
     * response carries {@code skillName} and {@code skillCategory}, and the market
     * lookup is keyed by skill id — so the display path touches the association on
     * every single node. Left lazy, a Backend roadmap issued 1.678 extra
     * round-trips per request, one per node, for a column the response always
     * reads. Measured in the plan for the same shape: {@code Index Scan using
     * skills_pkey ... loops=1678}.
     *
     * <p>Both fetches are to-one, so no cartesian product: the row count is
     * unchanged and no {@code DISTINCT} is needed.
     */
    @Query("SELECT sn FROM SkillNode sn "
            + "LEFT JOIN FETCH sn.parentNode "
            + "LEFT JOIN FETCH sn.skill "
            + "WHERE sn.careerRole.careerId = :careerId AND (sn.status IS NULL OR sn.status <> 'DRAFT') "
            + "ORDER BY sn.depth ASC NULLS LAST, sn.sortOrder ASC NULLS LAST, sn.nodeName ASC")
    List<SkillNode> findPublishedOrderedForCareer(UUID careerId);

    List<SkillNode> findBySkill_SkillIdAndCareerRole_CareerId(UUID skillId, UUID careerId);
    boolean existsByParentNode_NodeId(UUID nodeId);
    List<SkillNode> findByParentNode_NodeId(UUID nodeId);
    boolean existsByPreviousNode_NodeId(UUID nodeId);

    @Query("SELECT COUNT(sn) FROM SkillNode sn WHERE sn.careerRole.careerId = :careerId")
    int findTotalNodeOfRoadmap(UUID careerId);

    // Number of distinct careers that already have at least one roadmap node.
    @Query("SELECT COUNT(DISTINCT sn.careerRole.careerId) FROM SkillNode sn")
    long countDistinctCareersWithNodes();

}
