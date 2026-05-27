package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.internal.OAuth2UserInfoInternal;
import com.inteliroadmap.backend.domain.dto.internal.info.GitHubOauth2UserInfo;
import com.inteliroadmap.backend.domain.dto.internal.info.GoogleOAuth2UserInfo;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;

import java.util.Map;

public class OAuth2UserService {

    private OAuth2UserInfoInternal oAuth2UserInfoInternal;


    /**
     * Create OAuth2UserInfoInternal based on provider name
     *
     * @param providerName OAuth2 provider name (google, github)
     * @param attributes   Raw attributes from OAuth2 provider
     * @return OAuth2UserInfoInternal implementation
     * @throws ResourceNotFoundException if provider is not supported
     */
    private OAuth2UserInfoInternal create(String providerName, Map<String, Object> attributes) {
        return switch (providerName.toLowerCase()) {
            case "google" -> new GoogleOAuth2UserInfo(attributes);
            case "github" -> new GitHubOauth2UserInfo(attributes);
            default -> throw new ResourceNotFoundException("Unknown provider name: " + providerName);
        };
    }

}
