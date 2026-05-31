package com.inteliroadmap.backend.domain.dto.internal.info;

import com.inteliroadmap.backend.domain.dto.internal.OAuth2UserInfoInternal;

import java.util.Map;

public class GoogleOAuth2UserInfo extends OAuth2UserInfoInternal {

    public GoogleOAuth2UserInfo(Map<String, Object> attributes) {super(attributes);}

    @Override
    public String getProviderId() {
        return (String) attributes.get("sub");
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public String getFullName() {
        return (String) attributes.get("name");
    }
}
