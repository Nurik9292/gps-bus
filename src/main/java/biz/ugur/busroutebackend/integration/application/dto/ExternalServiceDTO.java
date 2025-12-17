package biz.ugur.busroutebackend.integration.application.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;


@Builder
public record ExternalServiceDTO(
        String id,
        String name,
        String description,
        String apiToken,
        String maskedToken,
        Boolean isActive,
        List<String> allowedEndpoints,
        Integer rateLimitPerMinute,
        Boolean canManageClients,
        LocalDateTime lastUsedAt,
        String createdByAdminId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
