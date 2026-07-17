package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.FptSubjectResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface FptSubjectResourceRepository extends JpaRepository<FptSubjectResource, UUID> {

    List<FptSubjectResource> findBySubjectCodeInOrderBySubjectCodeAscOrderIndexAsc(Collection<String> subjectCodes);

    /** Rows that reference a real file upstream — the only ones there is anything to mirror. */
    @Query("select f from FptSubjectResource f where f.sourceUrl is not null "
            + "order by f.subjectCode asc, f.orderIndex asc")
    List<FptSubjectResource> findMirrorCandidates();

    @Query("select f from FptSubjectResource f where f.sourceUrl is not null "
            + "and f.subjectCode = :subjectCode order by f.orderIndex asc")
    List<FptSubjectResource> findMirrorCandidatesBySubject(String subjectCode);

    @Modifying
    @Transactional
    @Query("delete from FptSubjectResource f where f.subjectCode = :subjectCode")
    void deleteBySubjectCode(String subjectCode);
}
