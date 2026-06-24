package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.Feedback;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    List<Feedback> findTop5ByReceiver_UserIdOrderByCreateAtDesc(UUID userId);
    List<Feedback> findByReceiver(User receiver);

    @Query("SELECT f FROM Feedback f WHERE (f.sender = :user1 AND f.receiver = :user2) OR (f.sender = :user2 AND f.receiver = :user1) ORDER BY f.createAt DESC")
    List<Feedback> findBySenderOrReceiverOrderByCreateAtDesc(@Param("user1") User user1, @Param("user2") User user2);
}
