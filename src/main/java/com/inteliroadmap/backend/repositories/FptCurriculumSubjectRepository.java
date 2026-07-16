package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.FptCurriculumSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface FptCurriculumSubjectRepository
        extends JpaRepository<FptCurriculumSubject, FptCurriculumSubject.PK> {

    List<FptCurriculumSubject> findByCurriculumId(UUID curriculumId);

    List<FptCurriculumSubject> findByCurriculumIdAndSemesterLessThanEqual(UUID curriculumId, int semester);

    @Transactional
    void deleteByCurriculumId(UUID curriculumId);
}
