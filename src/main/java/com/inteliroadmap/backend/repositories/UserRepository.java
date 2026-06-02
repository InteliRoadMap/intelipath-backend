package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    User findByEmail(String email);
    User findByUsername(String username);
    boolean existsByUsernameOrEmail(String username, String email);
    boolean existsByUsername(String username);
}
