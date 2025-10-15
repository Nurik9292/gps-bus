package biz.ugur.busroutebackend.interfaces.rest.client.V1.response;

public record RefreshTokenResponse(
        String accessToken,
        String refreshToken,
        String message
) {}
