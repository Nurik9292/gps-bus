package biz.ugur.busroutebackend.integration.application.mapper;

import biz.ugur.busroutebackend.integration.application.dto.ExternalServiceDTO;
import biz.ugur.busroutebackend.integration.domain.model.ExternalService;


public class ExternalServiceDTOMapper {

    private ExternalServiceDTOMapper() {
    }

    public static ExternalServiceDTO toDTO(ExternalService service) {
        return ExternalServiceDTO.builder()
                .id(service.getId().getValue())
                .name(service.getName())
                .description(service.getDescription())
                .apiToken(null)
                .maskedToken(service.getApiToken().getMaskedValue())
                .isActive(service.getIsActive())
                .allowedEndpoints(service.getAllowedEndpoints())
                .rateLimitPerMinute(service.getRateLimitPerMinute())
                .canManageClients(service.getCanManageClients())
                .lastUsedAt(service.getLastUsedAt())
                .createdByAdminId(service.getCreatedByAdminId().getValue())
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .build();
    }


    public static ExternalServiceDTO toDTOWithToken(ExternalService service) {
        return ExternalServiceDTO.builder()
                .id(service.getId().getValue())
                .name(service.getName())
                .description(service.getDescription())
                .apiToken(service.getApiToken().getValue())
                .maskedToken(service.getApiToken().getMaskedValue())
                .isActive(service.getIsActive())
                .allowedEndpoints(service.getAllowedEndpoints())
                .rateLimitPerMinute(service.getRateLimitPerMinute())
                .canManageClients(service.getCanManageClients())
                .lastUsedAt(service.getLastUsedAt())
                .createdByAdminId(service.getCreatedByAdminId().getValue())
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .build();
    }
}
