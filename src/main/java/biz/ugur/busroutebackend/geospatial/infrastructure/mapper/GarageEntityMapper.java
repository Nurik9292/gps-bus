package biz.ugur.busroutebackend.geospatial.infrastructure.mapper;

import biz.ugur.busroutebackend.geospatial.domain.model.Garage;
import biz.ugur.busroutebackend.geospatial.domain.valueobject.GarageId;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.GarageBoundary;
import biz.ugur.busroutebackend.geospatial.infrastructure.persistence.entity.GarageEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class GarageEntityMapper {


    public GarageEntity toEntity(Garage domain) {
        if (domain == null) {
            return null;
        }

        return GarageEntity.builder()
                .id(domain.getId().getValue().toString())
                .name(domain.getName())
                .nameTm(domain.getNameTm())
                .nameEn(domain.getNameEn())
                .latitude(domain.getLocation().getLatitudeAsDouble())
                .longitude(domain.getLocation().getLongitudeAsDouble())
                .radiusMeters(domain.getRadiusMeters())
                .boundaryWkt(domain.hasBoundary() ? domain.getBoundary().toWKT() : null)
                .cityId(domain.getCityId())
                .isActive(domain.getIsActive())
                .version(domain.getVersion())
                .build();
    }

    public Garage toDomain(GarageEntity entity) {
        if (entity == null) {
            return null;
        }

        GarageBoundary boundary = parseBoundary(entity.getBoundaryWkt());

        Garage garage = new Garage.Builder()
                .id(new GarageId(entity.getId()))
                .name(entity.getName())
                .nameTm(entity.getNameTm())
                .nameEn(entity.getNameEn())
                .location(Coordinates.of(entity.getLatitude(), entity.getLongitude()))
                .radiusMeters(entity.getRadiusMeters())
                .boundary(boundary)
                .cityId(entity.getCityId())
                .isActive(entity.getIsActive())
                .build();

        garage.setCreatedAt(entity.getCreatedAt());
        garage.setUpdatedAt(entity.getUpdatedAt());
        garage.setVersion(entity.getVersion());

        return garage;
    }

    private GarageBoundary parseBoundary(String boundaryWkt) {
        if (boundaryWkt == null || boundaryWkt.trim().isEmpty()) {
            return null;
        }

        try {
            return GarageBoundary.fromWKT(boundaryWkt);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to parse garage boundary WKT: {}", e.getMessage());
            return null;
        }
    }
}
