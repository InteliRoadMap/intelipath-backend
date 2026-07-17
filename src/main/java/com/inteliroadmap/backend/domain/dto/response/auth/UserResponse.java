package com.inteliroadmap.backend.domain.dto.response.auth;

import com.inteliroadmap.backend.domain.enums.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private String id;
    private String email;
    private String fullName;
    private String role;
    private String avatarUrl;

    /** Read-only: decides whether the client offers FPT coursework at all. Never accepted from a request. */
    private AccountType accountType;
}
