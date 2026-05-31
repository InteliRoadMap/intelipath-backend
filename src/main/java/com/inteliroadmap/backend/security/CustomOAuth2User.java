package com.inteliroadmap.backend.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;

/**
 * Custom implementation of OAuth2User.
 * This class holds the user's details retrieved from the OAuth2 provider (e.g., Google)
 * and the specific email and roles that our application uses for authorization and JWT generation.
 */
public class CustomOAuth2User implements OAuth2User {

    private final OAuth2User oauth2User;

    @Getter
    private final String email;

    @Getter
    private final String role;

    public CustomOAuth2User(OAuth2User oauth2User, String email, String role) {
        this.oauth2User = oauth2User;
        this.email = email;
        this.role = role;
    }

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
