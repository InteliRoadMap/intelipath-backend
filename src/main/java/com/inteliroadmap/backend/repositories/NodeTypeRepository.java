package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.NodeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NodeTypeRepository extends JpaRepository<NodeType, UUID> {
}
