package com.inteliroadmap.backend.domain.dto.response;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RefreshResponse {

    private String accessToken;
    private String expiresIn;
}
