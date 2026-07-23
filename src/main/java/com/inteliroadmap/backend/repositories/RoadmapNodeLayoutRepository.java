package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.RoadmapNodeLayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoadmapNodeLayoutRepository extends JpaRepository<RoadmapNodeLayout, UUID> {

    Optional<RoadmapNodeLayout> findByNodeId(UUID nodeId);

    List<RoadmapNodeLayout> findByNodeIdIn(Collection<UUID> nodeIds);
}
