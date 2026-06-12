package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.CareerRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public interface CareerRoleRepository extends JpaRepository<CareerRole, UUID> {
    CareerRole findByCareerId(UUID careerId);

    CareerRole findByCareerName(String careerName);

    List<CareerRole> findByCareerNameContainingIgnoreCase(String careerName);
}
