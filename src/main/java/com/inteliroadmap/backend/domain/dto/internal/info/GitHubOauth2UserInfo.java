package com.inteliroadmap.backend.domain.dto.internal.info;

import com.inteliroadmap.backend.domain.dto.internal.OAuth2UserInfoInternal;

import java.util.Map;

public class GitHubOauth2UserInfo extends OAuth2UserInfoInternal {

    public GitHubOauth2UserInfo(Map<String, Object> attributes) {
        super(attributes);
    }

    @Override
    public String getProviderId() {
        return attributes.get("id") != null ? String.valueOf(attributes.get("id")) : null;
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public String getFullName() {
        String name = (String) attributes.get("name");
        return name != null ? name : (String) attributes.get("login");
    }

    @Override
    public String getBio() {
        return (String) attributes.get("bio");
    }

    //GitHub account url
    @Override
    public String getHtmlUrl() {
        return (String) attributes.get("html_url");
    }
}
