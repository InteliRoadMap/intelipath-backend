package com.inteliroadmap.backend.domain.dto.response.auth;


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

    /** Access-token expiry as ISO-8601 UTC ("2026-07-16T14:29:16Z"). The zone suffix is not
     *  cosmetic: without it a client in another timezone reads the instant as its own local
     *  time and schedules its token refresh in the past. */
    private String expiresIn;
}
