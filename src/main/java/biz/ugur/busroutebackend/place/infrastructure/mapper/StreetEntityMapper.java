package biz.ugur.busroutebackend.place.infrastructure.mapper;

import biz.ugur.busroutebackend.place.domain.model.Street;
import biz.ugur.busroutebackend.place.domain.valueobjects.StreetId;
import biz.ugur.busroutebackend.place.infrastructure.persistence.entity.StreetEntity;

public class StreetEntityMapper {

    private StreetEntityMapper() {}

    public static Street toDomain(StreetEntity entity) {
        Street street = Street.builder()
                .id(StreetId.of(entity.getId()))
                .name(entity.getName())
                .nameEn(entity.getNameEn())
                .nameTm(entity.getNameTm())
                .cityId(entity.getCityId())
                .isActive(entity.getIsActive())
                .build();

        street.setCreatedAt(entity.getCreatedAt());
        street.setUpdatedAt(entity.getUpdatedAt());
        street.setVersion(entity.getVersion());

        return street;
    }

    public static StreetEntity toEntity(Street street) {
        return StreetEntity.builder()
                .id(street.getId().getValue())
                .name(street.getName())
                .nameEn(street.getNameEn())
                .nameTm(street.getNameTm())
                .cityId(street.getCityId())
                .isActive(street.getIsActive())
                .createdAt(street.getCreatedAt())
                .updatedAt(street.getUpdatedAt())
                .version(street.getVersion())
                .build();
    }
}
