package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.StudentEducation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StudentEducationRepository extends JpaRepository<StudentEducation, UUID> {
    List<StudentEducation> findByUser_UserId(UUID userId);
    void deleteByUser_UserId(UUID userId);
}
