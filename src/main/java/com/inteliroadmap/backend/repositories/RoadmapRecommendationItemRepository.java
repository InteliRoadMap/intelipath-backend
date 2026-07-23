package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.RoadmapRecommendationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface RoadmapRecommendationItemRepository extends JpaRepository<RoadmapRecommendationItem, UUID> {

    List<RoadmapRecommendationItem> findByRecommendationId(UUID recommendationId);

    List<RoadmapRecommendationItem> findByRecommendationIdIn(Collection<UUID> recommendationIds);
}
