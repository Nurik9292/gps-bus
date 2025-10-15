package biz.ugur.busroutebackend.interfaces.rest.client.V1.response;

public record LoginResponse(
        String clientId,
        String accessToken,
        String refreshToken,
        String message,
        String status
) {}
