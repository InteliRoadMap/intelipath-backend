package com.inteliroadmap.backend.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;

/**
 * Custom implementation of OAuth2User.
 * This class holds the user's details retrieved from the OAuth2 provider (e.g., Google)
 * and the specific email and roles that our application uses for authorization and JWT generation.
 */
@Data
@Builder
@AllArgsConstructor
public class CustomOAuth2User implements OAuth2User {

    private final OAuth2User oauth2User;

    private final String email;

    private final String role;

    @Override
    public Map<String, Object> getAttributes() {
        return oauth2User.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return oauth2User.getAuthorities();
    }

    @Override
    public String getName() {
        return oauth2User.getName();
    }

}
