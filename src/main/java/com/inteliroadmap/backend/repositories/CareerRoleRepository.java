package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.CareerRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CareerRoleRepository extends JpaRepository<CareerRole, UUID> {
    CareerRole findByCareerId(UUID careerId);
    CareerRole findByCareerName(String careerName);
}
