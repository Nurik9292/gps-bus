package biz.ugur.busroutebackend.integration.application.dto;

import lombok.Builder;


@Builder
public record IntegrationClientTokenResponse(
        String clientId,
        String externalUserId,
        String accessToken,
        String refreshToken,
        Long expiresIn,
        String tokenType
) {
    public IntegrationClientTokenResponse(String clientId,
                                          String externalUserId,
                                          String accessToken,
                                          String refreshToken,
                                          Long expiresIn) {
        this(clientId, externalUserId, accessToken, refreshToken, expiresIn, "Bearer");
    }
}
