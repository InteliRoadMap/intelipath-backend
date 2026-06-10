package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.Feedback;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    List<Feedback> findTop5ByReceiver_UserIdOrderByCreateAtDesc(UUID userId);

    List<Feedback> findByReceiver(User receiver);

    Feedback findByFeedbackId(UUID feedbackId);

    List<Feedback> findBySenderOrReceiverOrderByCreateAtDesc(User sender, User receiver);
}
