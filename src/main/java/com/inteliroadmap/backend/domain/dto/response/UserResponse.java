package com.inteliroadmap.backend.domain.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private String accessToken;
    private String refreshToken;
    private String expiresIn;
    private String id;
    private String fullName;
    private String role;
}