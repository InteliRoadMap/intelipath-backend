package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.UpdateNodeProgressRequest;
import com.inteliroadmap.backend.domain.dto.response.roadmap.StudentRoadmapResponse;
import com.inteliroadmap.backend.domain.dto.response.roadmap.RoadmapNodeResponse;
import com.inteliroadmap.backend.domain.dto.response.plan.LearningPlanResponse;
import com.inteliroadmap.backend.domain.entity.Student;

import java.util.Set;
import java.util.UUID;

public interface RoadmapService {

    StudentRoadmapResponse getStudentRoadmap();

    /**
     * What this student should learn next, derived from their own evidence and
     * from live market demand rather than from the career's node catalog.
     *
     * <p>The roadmap endpoint answers "what exists here". This answers "what
     * should I do", which is a different question and cannot be reached by
     * filtering the first one.
     */
    LearningPlanResponse getStudentPlan();

    /**
     * As {@link #getStudentRoadmap()}, but with named topics opened up.
     *
     * <p>The default payload stops at the direct children of each root, because
     * the graded pool is now deep enough (Backend: 1.678 nodes, 8 levels) that
     * sending all of it is a page the browser cannot draw. Passing a node id here
     * asks for everything beneath it as well — which is what "go deeper on Java"
     * is, expressed as a request.
     *
     * @param expandedNodeIds topics to open; null or empty behaves exactly like
     *        the no-argument call
     */
    StudentRoadmapResponse getStudentRoadmap(Set<UUID> expandedNodeIds);

    /** Read-only roadmap snapshot for a portfolio owner or viewer. */
    StudentRoadmapResponse getStudentRoadmapForPortfolio(Student student);

    /**
     * One standalone roadmap under the career.
     *
     * <p>Languages, frameworks and database tracks are whole roadmaps of their
     * own; they are entered rather than walked past, so this serves the subtree
     * with a breadcrumb back out instead of folding it into the career path.
     *
     * <p>Sliced by the same visibility rules as the career roadmap, and opened
     * the same way — see {@link #getStudentRoadmap(Set)} for what
     * {@code expandedNodeIds} means.
     */
    StudentRoadmapResponse getStudentSubRoadmap(UUID rootNodeId, Set<UUID> expandedNodeIds);

    StudentRoadmapResponse getCareerRoadmapTemplate(UUID careerId);

    Integer getCareerProgress(UUID careerId);

    RoadmapNodeResponse getNodeDetail(UUID nodeId);

    void updateNodeProgress(UpdateNodeProgressRequest request);
}
