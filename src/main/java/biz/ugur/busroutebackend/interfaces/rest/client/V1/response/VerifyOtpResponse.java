package biz.ugur.busroutebackend.interfaces.rest.client.V1.response;

public record VerifyOtpResponse(
        String clientId,
        Boolean verified,
        String message,
        String status
) {}