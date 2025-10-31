package biz.ugur.busroutebackend.transport.infrastructure.mapper;

import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.infrastructure.persistence.entity.BusRouteEntity;
import org.springframework.stereotype.Component;


@Component
public class BusRouteEntityMapper {


    public BusRouteEntity toEntity(BusRoute domain) {
        if (domain == null) {
            return null;
        }

        return BusRouteEntity.builder()
                .id(domain.getId().getValue())
                .routeNumber(domain.getRouteNumber())
                .routeName(domain.getRouteName())
                .nameTm(domain.getNameTm())
                .nameEn(domain.getNameEn())
                .routeColor(domain.getRouteColor())
                .isActive(domain.getIsActive())
                .cityId(domain.getCityId())
                .estimatedDurationMinutes(domain.getEstimatedDurationMinutes())
                .routeGeometryForward(domain.getRouteGeometryForward())
                .routeGeometryBackward(domain.getRouteGeometryBackward())
                .totalDistanceForwardMeters(domain.getTotalDistanceForwardMeters())
                .totalDistanceBackwardMeters(domain.getTotalDistanceBackwardMeters())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .version(domain.getVersion())
                .build();
    }


    public BusRoute toDomain(BusRouteEntity entity) {
        if (entity == null) {
            return null;
        }

        return BusRoute.restore(
                BusRouteId.of(entity.getId()),
                entity.getRouteNumber(),
                entity.getRouteName(),
                entity.getNameTm(),
                entity.getNameEn(),
                entity.getRouteColor(),
                entity.getIsActive(),
                entity.getCityId(),
                entity.getEstimatedDurationMinutes(),
                entity.getRouteGeometryForward(),
                entity.getRouteGeometryBackward(),
                entity.getTotalDistanceForwardMeters(),
                entity.getTotalDistanceBackwardMeters(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
