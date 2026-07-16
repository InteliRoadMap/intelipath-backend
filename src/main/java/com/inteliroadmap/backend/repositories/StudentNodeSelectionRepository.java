package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.StudentNodeSelection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentNodeSelectionRepository extends JpaRepository<StudentNodeSelection, UUID> {

    List<StudentNodeSelection> findByStudent_UserId(UUID studentId);

    Optional<StudentNodeSelection> findByStudent_UserIdAndGroupNode_NodeId(UUID studentId, UUID groupNodeId);

    boolean existsByStudent_UserIdAndGroupNode_NodeId(UUID studentId, UUID groupNodeId);
}
