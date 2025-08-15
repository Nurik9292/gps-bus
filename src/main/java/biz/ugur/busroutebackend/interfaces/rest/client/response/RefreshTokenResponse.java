package biz.ugur.busroutebackend.interfaces.rest.client.response;

public record RefreshTokenResponse(
        String accessToken,
        String refreshToken,
        String message
) {}
