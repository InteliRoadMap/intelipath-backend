package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.FptSubjectClo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface FptSubjectCloRepository extends JpaRepository<FptSubjectClo, UUID> {

    List<FptSubjectClo> findBySubjectCodeInOrderBySubjectCodeAscOrderIndexAsc(Collection<String> subjectCodes);

    List<FptSubjectClo> findBySubjectCodeOrderByOrderIndexAsc(String subjectCode);

    @Modifying
    @Transactional
    @Query("delete from FptSubjectClo f where f.subjectCode = :subjectCode")
    void deleteBySubjectCode(String subjectCode);
}
