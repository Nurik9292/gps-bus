package biz.ugur.busroutebackend.interfaces.rest.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record AuthResponse(
        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("refresh_token")
        String refreshToken,

        @JsonProperty("token_type")
        String tokenType,

        @JsonProperty("expires_in")
        long expiresIn,

        @JsonProperty("admin")
        AdminProfileResponse admin
) {}