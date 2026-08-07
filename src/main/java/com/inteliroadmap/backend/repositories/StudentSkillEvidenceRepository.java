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

    /** FLM-sourced evidence for a student (detectedBy is "FLM:<subjectCode>"). */
    List<StudentSkillEvidence> findByUserIdAndDetectedByStartingWith(UUID userId, String detectedByPrefix);

    /**
     * Every evidence row one source produced, whatever became of it.
     *
     * <p>For a GitHub import the source URL is the repository URL, which makes this the
     * link from "the model claimed these skills" to "here is what the profile did with
     * each claim". Includes REJECTED rows on purpose: a claim that lost to a stronger
     * one is the most informative thing on the audit screen, and hiding it would leave
     * a skill the student was told about simply missing.
     */
    List<StudentSkillEvidence> findByUserIdAndSourceUrl(UUID userId, String sourceUrl);
}
