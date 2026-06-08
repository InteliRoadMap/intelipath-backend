package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.Feedback;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    List<Feedback> findByReceiver(User receiver);

    Feedback findByFeedbackId(UUID feedbackId);
}
