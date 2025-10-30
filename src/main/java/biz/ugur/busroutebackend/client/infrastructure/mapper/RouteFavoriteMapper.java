package biz.ugur.busroutebackend.client.infrastructure.mapper;

import biz.ugur.busroutebackend.client.domain.model.RouteFavorite;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.domain.valueobject.RouteFavoriteId;
import biz.ugur.busroutebackend.client.infrastructure.persistence.entity.RouteFavoriteEntity;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;

public class RouteFavoriteMapper {

    private RouteFavoriteMapper() {
    }

    public static RouteFavorite toDomain(RouteFavoriteEntity entity) {
        return RouteFavorite.fromDatabase(
                RouteFavoriteId.of(entity.getId()),
                ClientId.of(entity.getClientId()),
                BusRouteId.of(entity.getRouteId()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    public static RouteFavoriteEntity toEntity(RouteFavorite routeFavorite) {
        return RouteFavoriteEntity.builder()
                .id(routeFavorite.getId().getValue())
                .clientId(routeFavorite.getClientId().getValue())
                .routeId(routeFavorite.getRouteId().getValue())
                .createdAt(routeFavorite.getCreatedAt())
                .updatedAt(routeFavorite.getUpdatedAt())
                .version(routeFavorite.getVersion())
                .build();
    }
}
