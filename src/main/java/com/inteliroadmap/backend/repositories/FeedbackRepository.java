package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.Feedback;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.FeedbackStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    Feedback findByFeedbackId(UUID feedbackId);
    List<Feedback> findTop5ByReceiver_UserIdOrderByCreatedAtDesc(UUID receiverId);

    // Notification inbox: newest feedback the student hasn't dismissed (status != DELETED).
    List<Feedback> findTop5ByReceiver_UserIdAndStatusNotOrderByCreatedAtDesc(
            UUID receiverId, FeedbackStatus status);

    List<Feedback> findBySender(User receiver);
    List<Feedback> findByReceiver(User receiver);

    @Query("SELECT COUNT(f) FROM Feedback f WHERE f.sender.userId = :senderId AND f.createdAt >= :since")
    long countFeedbacksBySenderIdSince(@Param("senderId") UUID senderId, @Param("since") java.time.LocalDateTime since);

    @Query("SELECT f FROM Feedback f WHERE (f.sender.userId = :userId1 AND f.receiver.userId = :userId2) OR (f.sender.userId = :userId2 AND f.receiver.userId = :userId1) ORDER BY f.createdAt DESC")
    List<Feedback> findBySenderOrReceiverOrderByCreatedAtDesc(@Param("userId1") UUID userId1, @Param("userId2") UUID userId2);

    @Query("SELECT f FROM Feedback f WHERE f.sender.userId = :senderId ORDER BY f.createdAt DESC")
    List<Feedback> findBySender_UserIdOrderByCreatedAtDesc(@Param("senderId") UUID senderId);
}
