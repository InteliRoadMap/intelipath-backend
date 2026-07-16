package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
//    boolean deleteByToken(String token);
//    RefreshToken findByToken(String token);
//    void deleteByUser_UserId(UUID userId);

    @Transactional
    long deleteByToken(String token);

    RefreshToken findByToken(String token);

    @Transactional
    void deleteByUser_UserId(UUID userId);

    // Purge a user's tokens whose (grace-adjusted) expiry has already passed, called during
    // rotation so short-lived rotated stubs don't accumulate.
    @Transactional
    void deleteByUser_UserIdAndExpiredAtBefore(UUID userId, LocalDateTime cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select refreshToken from RefreshToken refreshToken where refreshToken.token = :token")
    Optional<RefreshToken> findByTokenForUpdate(String token);
}
