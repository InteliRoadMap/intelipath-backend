package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.domain.enums.UserRole;
import com.inteliroadmap.backend.domain.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    User findByUserId(UUID userId);
    User findByEmail(String email);

    /** Login lookup for counselor-provisioned FPT accounts; OAuth accounts have no username. */
    Optional<User> findByUsername(String username);

    /**
     * The mentor directory a student picks from. Suspended mentors are excluded:
     * they cannot answer a review request, so offering them would only produce a
     * request that never gets read.
     */
    Page<User> findByRoleAndUserStatusOrderByFullNameAsc(UserRole role, UserStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.email = :email")
    Optional<User> findByEmailForUpdate(String email);

    @Query("SELECT u FROM User u ORDER BY u.createdAt DESC")
    List<User> findAllUsers();

    long countByCreatedAtAfter(java.time.LocalDateTime dateTime);
    long countByCreatedAtBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);
}
