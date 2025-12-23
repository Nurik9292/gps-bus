package biz.ugur.busroutebackend.geospatial.infrastructure.mapper;

import biz.ugur.busroutebackend.geospatial.domain.model.Garage;
import biz.ugur.busroutebackend.geospatial.domain.valueobject.GarageId;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.geospatial.infrastructure.persistence.entity.GarageEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper between Garage domain model and GarageEntity persistence model
 */
@Component
public class GarageEntityMapper {

    /**
     * Convert domain model to persistence entity
     */
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
                .cityId(domain.getCityId())
                .isActive(domain.getIsActive())
                .version(domain.getVersion())
                .build();
    }

    /**
     * Convert persistence entity to domain model
     */
    public Garage toDomain(GarageEntity entity) {
        if (entity == null) {
            return null;
        }

        Garage garage = new Garage.Builder()
                .id(new GarageId(entity.getId()))
                .name(entity.getName())
                .nameTm(entity.getNameTm())
                .nameEn(entity.getNameEn())
                .location(Coordinates.of(entity.getLatitude(), entity.getLongitude()))
                .radiusMeters(entity.getRadiusMeters())
                .cityId(entity.getCityId())
                .isActive(entity.getIsActive())
                .build();

        // Set metadata fields
        garage.setCreatedAt(entity.getCreatedAt());
        garage.setUpdatedAt(entity.getUpdatedAt());
        garage.setVersion(entity.getVersion());

        return garage;
    }
}
