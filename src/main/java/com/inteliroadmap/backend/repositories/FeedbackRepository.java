package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    List<Feedback> findTop5ByReceiver_UserIdOrderByCreateAtDesc(UUID userId);
}
