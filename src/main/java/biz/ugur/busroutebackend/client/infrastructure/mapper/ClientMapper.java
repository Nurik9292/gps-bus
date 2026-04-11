package biz.ugur.busroutebackend.client.infrastructure.mapper;

import biz.ugur.busroutebackend.client.domain.enums.ClientStatus;
import biz.ugur.busroutebackend.client.domain.enums.Platform;
import biz.ugur.busroutebackend.client.domain.model.Client;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.infrastructure.persistence.entity.ClientEntity;

public class ClientMapper {

    private ClientMapper() {
    }

    public static Client toDomain(ClientEntity entity) {
        return Client.fromDatabase(
                ClientId.of(entity.getId()),
                entity.getName(),
                entity.getPhone(),
                entity.getOtp(),
                entity.getOtpVerify(),
                Platform.valueOf(entity.getPlatform()),
                ClientStatus.valueOf(entity.getStatus()),
                entity.getLastActivity(),
                entity.getAccessToken(),
                entity.getRefreshToken(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion(),
                entity.getCreatedByServiceId(),
                entity.getExternalUserId()
        );
    }

    public static ClientEntity toEntity(Client client) {
        return ClientEntity.builder()
                .id(client.getId().getValue())
                .name(client.getName())
                .phone(client.getPhoneNumber())
                .otp(client.getOtpCode())
                .otpVerify(client.getOtpVerify())
                .platform(client.getPlatform().name())
                .status(client.getStatus().name())
                .lastActivity(client.getLastActivity())
                .accessToken(client.getAccessToken())
                .refreshToken(client.getRefreshToken())
                .createdAt(client.getCreatedAt())
                .updatedAt(client.getUpdatedAt())
                .version(client.getVersion())
                .createdByServiceId(client.getCreatedByServiceId())
                .externalUserId(client.getExternalUserId())
                .build();
    }
}
