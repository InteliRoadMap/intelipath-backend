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

    @Modifying
    @Transactional
    @Query("delete from FptSubjectResource f where f.subjectCode = :subjectCode")
    void deleteBySubjectCode(String subjectCode);
}
