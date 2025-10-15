package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.admin;

import biz.ugur.busroutebackend.admin.application.usecase.auth.LoginUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.auth.RefreshTokenUseCase;
import com.fasterxml.jackson.annotation.JsonProperty;

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
) {
        public static AuthResponse fromLogin(LoginUseCase.Response result) {
                return new AuthResponse(
                        result.accessToken(),
                        result.refreshToken(),
                        result.tokenType(),
                        result.expiresIn(),
                        new AdminProfileResponse(
                                result.admin().getId().getValue(),
                                result.admin().getUsername(),
                                result.admin().getFullName(),
                                result.admin().getIsSuperAdmin(),
                                result.admin().getIsActive(),
                                result.admin().getLastLoginAt(),
                                result.admin().getCreatedAt(),
                                result.admin().getUpdatedAt(),
                                result.admin().getAvatar()
                        )
                );
        }

        public static AuthResponse fromRefresh(RefreshTokenUseCase.Response result) {
                return  new AuthResponse(
                        result.accessToken(),
                        result.refreshToken(),
                        result.tokenType(),
                        result.expiresIn(),
                        new AdminProfileResponse(
                                result.admin().getId().getValue(),
                                result.admin().getUsername(),
                                result.admin().getFullName(),
                                result.admin().getIsSuperAdmin(),
                                result.admin().getIsActive(),
                                result.admin().getLastLoginAt(),
                                result.admin().getCreatedAt(),
                                result.admin().getUpdatedAt(),
                                result.admin().getAvatar()
                        )
                );
        }

}