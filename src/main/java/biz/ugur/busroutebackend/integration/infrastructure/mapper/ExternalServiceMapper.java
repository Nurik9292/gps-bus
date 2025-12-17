package biz.ugur.busroutebackend.integration.infrastructure.mapper;

import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.integration.domain.model.ExternalService;
import biz.ugur.busroutebackend.integration.domain.valueobjects.ApiToken;
import biz.ugur.busroutebackend.integration.domain.valueobjects.ExternalServiceId;
import biz.ugur.busroutebackend.integration.infrastructure.persistence.entity.ExternalServiceEntity;

public class ExternalServiceMapper {

    private ExternalServiceMapper() {
    }

    public static ExternalService toDomain(ExternalServiceEntity entity) {
        return ExternalService.fromDatabase(
                ExternalServiceId.of(entity.getId()),
                entity.getName(),
                entity.getDescription(),
                ApiToken.of(entity.getApiToken()),
                entity.getIsActive(),
                entity.getAllowedEndpoints(),
                entity.getRateLimitPerMinute(),
                entity.getCanManageClients(),
                entity.getLastUsedAt(),
                AdminId.of(entity.getCreatedByAdminId()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    public static ExternalServiceEntity toEntity(ExternalService domain) {
        return ExternalServiceEntity.builder()
                .id(domain.getId().getValue())
                .name(domain.getName())
                .description(domain.getDescription())
                .apiToken(domain.getApiToken().getValue())
                .isActive(domain.getIsActive())
                .allowedEndpoints(domain.getAllowedEndpoints())
                .rateLimitPerMinute(domain.getRateLimitPerMinute())
                .canManageClients(domain.getCanManageClients())
                .lastUsedAt(domain.getLastUsedAt())
                .createdByAdminId(domain.getCreatedByAdminId().getValue())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .version(domain.getVersion())
                .build();
    }
}
