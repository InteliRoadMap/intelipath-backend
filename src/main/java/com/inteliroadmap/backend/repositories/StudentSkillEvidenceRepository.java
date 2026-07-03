package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.StudentSkillEvidence;
import com.inteliroadmap.backend.domain.enums.EvidenceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface StudentSkillEvidenceRepository extends JpaRepository<StudentSkillEvidence, UUID> {

    List<StudentSkillEvidence> findByUserIdAndStatusIn(UUID userId, Collection<EvidenceStatus> statuses);
}
