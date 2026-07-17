package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.RoadmapRecommendation;
import com.inteliroadmap.backend.domain.enums.RecommendationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RoadmapRecommendationRepository extends JpaRepository<RoadmapRecommendation, UUID> {

    List<RoadmapRecommendation> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, RecommendationStatus status);
}
