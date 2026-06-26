package biz.ugur.busroutebackend.integration.application.dto;


public record IntegrationClientTokenRequest(
        String clientId,
        String externalUserId,
        String phone
) {
}
