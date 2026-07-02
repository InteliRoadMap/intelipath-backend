package com.inteliroadmap.backend.domain.dto.response;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RefreshResponse {

    private String accessToken;

    @JsonIgnore
    private String refreshToken;

    private String expiresIn;
}
