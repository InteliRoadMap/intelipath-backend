package com.inteliroadmap.backend.domain.dto.internal;


import java.util.Map;

public abstract class OAuth2UserInfoInternal {

    protected Map<String, Object> attributes;

    public OAuth2UserInfoInternal(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public abstract String getProviderId();
    public abstract String getEmail();
    public abstract String getFullName();
    
    public String getBio() { return null; }
    public String getHtmlUrl() { return null; }

}
