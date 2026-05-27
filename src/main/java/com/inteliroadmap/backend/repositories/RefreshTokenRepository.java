package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    boolean deleteByToken(String token);
    RefreshToken findByToken(String token);
}
