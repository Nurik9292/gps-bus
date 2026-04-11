package biz.ugur.busroutebackend.client.infrastructure.mapper;

import biz.ugur.busroutebackend.client.domain.model.StopFavorite;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.domain.valueobject.StopFavoriteId;
import biz.ugur.busroutebackend.client.infrastructure.persistence.entity.StopFavoriteEntity;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;

public class StopFavoriteMapper {

    private StopFavoriteMapper() {
    }

    public static StopFavorite toDomain(StopFavoriteEntity entity) {
        return StopFavorite.fromDatabase(
                StopFavoriteId.of(entity.getId()),
                ClientId.of(entity.getClientId()),
                BusStopId.of(entity.getStopId()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    public static StopFavoriteEntity toEntity(StopFavorite stopFavorite) {
        return StopFavoriteEntity.builder()
                .id(stopFavorite.getId().getValue())
                .clientId(stopFavorite.getClientId().getValue())
                .stopId(stopFavorite.getStopId().getValue())
                .createdAt(stopFavorite.getCreatedAt())
                .updatedAt(stopFavorite.getUpdatedAt())
                .version(stopFavorite.getVersion())
                .build();
    }
}
