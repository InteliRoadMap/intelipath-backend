package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.FptCurriculumSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface FptCurriculumSubjectRepository
        extends JpaRepository<FptCurriculumSubject, FptCurriculumSubject.PK> {

    /**
     * Every row of a curriculum, all combos included — for admin/import paths only.
     * Student-facing reads must use the combo-scoped queries below, or a Java student
     * is shown (and can be credited with) the .NET combo's subjects.
     */
    List<FptCurriculumSubject> findByCurriculumId(UUID curriculumId);

    /**
     * The subjects a student on this curriculum actually takes: the trunk plus their own
     * combo. A null comboCode matches trunk only — JPQL equality never holds for null —
     * which is deliberate: a student who hasn't picked sees no combo subjects rather than
     * someone else's.
     */
    @Query("SELECT cs FROM FptCurriculumSubject cs "
            + "WHERE cs.curriculumId = :curriculumId "
            + "AND (cs.comboCode IS NULL OR cs.comboCode = :comboCode)")
    List<FptCurriculumSubject> findForStudent(@Param("curriculumId") UUID curriculumId,
                                              @Param("comboCode") String comboCode);

    /**
     * Same scoping as {@link #findForStudent}, up to and including a term. Backs "I have
     * finished term N", so an unscoped version here would mark another combo's subjects
     * as passed and feed that into the student's skill evidence.
     */
    @Query("SELECT cs FROM FptCurriculumSubject cs "
            + "WHERE cs.curriculumId = :curriculumId "
            + "AND cs.semester <= :semester "
            + "AND (cs.comboCode IS NULL OR cs.comboCode = :comboCode)")
    List<FptCurriculumSubject> findForStudentUpToTerm(@Param("curriculumId") UUID curriculumId,
                                                      @Param("semester") int semester,
                                                      @Param("comboCode") String comboCode);

    /** Distinct combos offered by a curriculum, for the student's combo picker. */
    @Query("SELECT DISTINCT cs.comboCode, cs.comboName FROM FptCurriculumSubject cs "
            + "WHERE cs.curriculumId = :curriculumId AND cs.comboCode IS NOT NULL "
            + "ORDER BY cs.comboCode")
    List<Object[]> findCombos(@Param("curriculumId") UUID curriculumId);

    @Transactional
    void deleteByCurriculumId(UUID curriculumId);
}
