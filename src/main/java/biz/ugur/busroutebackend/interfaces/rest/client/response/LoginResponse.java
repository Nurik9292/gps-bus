package biz.ugur.busroutebackend.interfaces.rest.client.response;

public record LoginResponse(
        String clientId,
        String accessToken,
        String refreshToken,
        String message,
        String status
) {}
