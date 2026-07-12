package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
//    boolean deleteByToken(String token);
//    RefreshToken findByToken(String token);
//    void deleteByUser_UserId(UUID userId);

    @Transactional
    boolean deleteByToken(String token);

    RefreshToken findByToken(String token);

    @Transactional
    void deleteByUser_UserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select refreshToken from RefreshToken refreshToken where refreshToken.token = :token")
    Optional<RefreshToken> findByTokenForUpdate(String token);
}
