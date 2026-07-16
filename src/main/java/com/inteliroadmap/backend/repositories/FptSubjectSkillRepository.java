package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.FptSubjectSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Repository
public interface FptSubjectSkillRepository extends JpaRepository<FptSubjectSkill, java.util.UUID> {

    List<FptSubjectSkill> findBySubjectCode(String subjectCode);

    List<FptSubjectSkill> findBySubjectCodeIn(Collection<String> subjectCodes);

    List<FptSubjectSkill> findBySkillNameIgnoreCase(String skillName);

    // Bulk delete runs immediately, so a re-import can insert the same
    // (subject_code, skill_name) without clashing with uq_fss.
    @Modifying
    @Transactional
    @Query("delete from FptSubjectSkill f where f.subjectCode = :subjectCode")
    void deleteBySubjectCode(String subjectCode);
}
