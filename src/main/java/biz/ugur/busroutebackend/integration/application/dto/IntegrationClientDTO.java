package biz.ugur.busroutebackend.integration.application.dto;

import lombok.Builder;

import java.time.LocalDateTime;


@Builder
public record IntegrationClientDTO(
        String clientId,
        String name,
        String externalUserId,
        String status,
        LocalDateTime lastActivity,
        LocalDateTime createdAt
) {
}
