package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.PortfolioReviewRequest;
import com.inteliroadmap.backend.domain.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import com.inteliroadmap.backend.domain.entity.User;

@Repository
public interface PortfolioReviewRequestRepository extends JpaRepository<PortfolioReviewRequest, UUID> {

    long countByMentor_UserIdAndStatus(UUID mentorId, ReviewStatus status);

    Page<PortfolioReviewRequest> findByMentor_UserIdAndStatus(UUID mentorId, ReviewStatus status, Pageable pageable);

    boolean existsByStudent_UserIdAndMentor_UserIdAndStatus(UUID studentId, UUID mentorId, ReviewStatus status);

    /** A submitted review creates the only mentor-to-student audit-read grant. */
    boolean existsByStudent_UserIdAndMentor_UserId(UUID studentId, UUID mentorId);

    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (r.resolved_at - r.create_at))) FROM portfolio_review_requests r " +
            "WHERE r.mentor_id = :mentorId AND r.status = 'REVIEWED'", nativeQuery = true)
    Double getAverageResponseTimeInSecondsByMentorId(@Param("mentorId") UUID mentorId);

    @Query("SELECT DISTINCT r.student FROM PortfolioReviewRequest r WHERE r.mentor.userId = :mentorId")
    Page<User> findDistinctStudentsByMentorId(@Param("mentorId") UUID mentorId, Pageable pageable);

    @Query("SELECT COUNT(DISTINCT r.student) FROM PortfolioReviewRequest r WHERE r.mentor.userId = :mentorId")
    long countDistinctStudentsByMentorId(@Param("mentorId") UUID mentorId);
}
