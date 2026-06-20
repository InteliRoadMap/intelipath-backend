package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    User findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.email = :email")
    Optional<User> findByEmailForUpdate(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u ORDER BY u.createAt DESC")
    List<User> findAllUsers();

    User findByUserId(UUID userId);

    User findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(String email, String fullName);
}
