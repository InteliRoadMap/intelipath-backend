package com.inteliroadmap.backend.mappers;

import com.inteliroadmap.backend.domain.dto.response.auth.UserResponse;
import com.inteliroadmap.backend.domain.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getUserId().toString())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .avatarUrl(user.getAvatarUrl())
                .accountType(user.getAccountType())
                .build();
    }
}
