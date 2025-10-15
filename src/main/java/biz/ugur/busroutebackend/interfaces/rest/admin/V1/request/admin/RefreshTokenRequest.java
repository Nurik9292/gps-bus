package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.admin;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {}