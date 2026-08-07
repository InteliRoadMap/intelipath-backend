package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.StudentAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentAssessmentRepository extends JpaRepository<StudentAssessment, UUID> {

    /**
     * The student's most recent run. Empty means they have never taken the
     * assessment, which is a normal state — taking it is optional.
     */
    Optional<StudentAssessment> findFirstByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status);

    List<StudentAssessment> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<StudentAssessment> findByUserIdAndCareerIdAndStatusOrderByCreatedAtDesc(
            UUID userId, UUID careerId, String status);
}
