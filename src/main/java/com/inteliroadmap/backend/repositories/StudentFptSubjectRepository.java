package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.StudentFptSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentFptSubjectRepository extends JpaRepository<StudentFptSubject, UUID> {

    List<StudentFptSubject> findByUserId(UUID userId);

    Optional<StudentFptSubject> findByUserIdAndSubjectCode(UUID userId, String subjectCode);
}
