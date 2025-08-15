package biz.ugur.busroutebackend.interfaces.rest.client.response;

public record RegisterResponse(
        String clientId,
        String name,
        String phone,
        String message,
        String status
) {}
