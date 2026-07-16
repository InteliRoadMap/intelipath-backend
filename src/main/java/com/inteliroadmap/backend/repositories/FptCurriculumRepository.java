package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.FptCurriculum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FptCurriculumRepository extends JpaRepository<FptCurriculum, UUID> {

    Optional<FptCurriculum> findByCode(String code);

    List<FptCurriculum> findByProgramIgnoreCaseAndCohortOrderByEffectiveDateDescCodeAsc(String program, Integer cohort);

    List<FptCurriculum> findByProgramIgnoreCaseOrderByCohortDescCodeAsc(String program);

    Optional<FptCurriculum> findFirstByIsDefaultTrue();

    List<FptCurriculum> findAllByOrderByProgramAscCohortDescCodeAsc();
}
