package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.FptSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FptSubjectRepository extends JpaRepository<FptSubject, String> {

    List<FptSubject> findAllByOrderByCodeAsc();

    List<FptSubject> findByCodeStartingWithOrderByCodeAsc(String prefix);
}
