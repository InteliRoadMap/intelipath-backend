package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    // Locked so two concurrent redemptions of the same link cannot both pass the
    // used_at check and reset the password twice.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t where t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(String tokenHash);

    // Requesting a new link invalidates any still-pending ones for that user, so an
    // older email can't be used after a newer request.
    @Transactional
    void deleteByUser_UserId(UUID userId);
}
