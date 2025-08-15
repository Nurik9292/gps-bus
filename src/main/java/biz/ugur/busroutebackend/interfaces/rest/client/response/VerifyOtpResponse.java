package biz.ugur.busroutebackend.interfaces.rest.client.response;

public record VerifyOtpResponse(
        String clientId,
        Boolean verified,
        String message,
        String status
) {}