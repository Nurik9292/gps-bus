package biz.ugur.busroutebackend.transport.infrastructure.mapper;

import biz.ugur.busroutebackend.transport.domain.model.RouteAlternative;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteAlternativeId;
import biz.ugur.busroutebackend.transport.infrastructure.persistence.entity.RouteAlternativeEntity;
import org.springframework.stereotype.Component;

@Component
public class RouteAlternativeEntityMapper {

    public RouteAlternativeEntity toEntity(RouteAlternative domain) {
        if (domain == null) {
            return null;
        }

        return RouteAlternativeEntity.builder()
                .id(domain.getId().getValue())
                .primaryRouteId(domain.getPrimaryRouteId().getValue())
                .alternativeRouteId(domain.getAlternativeRouteId().getValue())
                .priority(domain.getPriority())
                .description(domain.getDescription())
                .descriptionTm(domain.getDescriptionTm())
                .descriptionEn(domain.getDescriptionEn())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .version(domain.getVersion())
                .build();
    }

    public RouteAlternative toDomain(RouteAlternativeEntity entity) {
        if (entity == null) {
            return null;
        }

        return RouteAlternative.restore(
                RouteAlternativeId.of(entity.getId()),
                BusRouteId.of(entity.getPrimaryRouteId()),
                BusRouteId.of(entity.getAlternativeRouteId()),
                entity.getPriority(),
                entity.getDescription(),
                entity.getDescriptionTm(),
                entity.getDescriptionEn(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
