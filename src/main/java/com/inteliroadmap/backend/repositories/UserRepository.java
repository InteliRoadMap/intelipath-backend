package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository
        extends JpaRepository<User, UUID> {

    // Tìm user theo email (dùng cho login)
    Optional<User> findByEmail(String email);

    // Kiểm tra email đã tồn tại chưa (dùng cho register)
    boolean existsByEmail(String email);
}
