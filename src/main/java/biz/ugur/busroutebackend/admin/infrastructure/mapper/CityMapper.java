package biz.ugur.busroutebackend.admin.infrastructure.mapper;

import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.admin.domain.valueobjects.CityId;
import biz.ugur.busroutebackend.admin.infrastructure.persistence.entity.CityEntity;

/**
 * Mapper between City domain model and CityEntity persistence model.
 */
public class CityMapper {

    private CityMapper() {}

    /**
     * Converts CityEntity to City domain model.
     */
    public static City toDomain(CityEntity entity) {
        City city = City.builder()
                .id(CityId.of(entity.getId()))
                .name(entity.getName())
                .nameTm(entity.getNameTm())
                .isActive(entity.getIsActive())
                .displayOrder(entity.getDisplayOrder())
                .build();

        // Set mutable persistence fields
        city.setCreatedAt(entity.getCreatedAt());
        city.setUpdatedAt(entity.getUpdatedAt());
        city.setVersion(entity.getVersion());

        return city;
    }

    /**
     * Converts City domain model to CityEntity.
     */
    public static CityEntity toEntity(City city) {
        return CityEntity.builder()
                .id(city.getId().getValue())
                .name(city.getName())
                .nameTm(city.getNameTm())
                .isActive(city.getIsActive())
                .displayOrder(city.getDisplayOrder())
                .createdAt(city.getCreatedAt())
                .updatedAt(city.getUpdatedAt())
                .version(city.getVersion())
                .build();
    }
}
