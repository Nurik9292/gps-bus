package biz.ugur.busroutebackend.place.infrastructure.mapper;

import biz.ugur.busroutebackend.place.domain.model.Place;
import biz.ugur.busroutebackend.place.domain.model.PlaceCategory;
import biz.ugur.busroutebackend.place.domain.valueobjects.PlaceId;
import biz.ugur.busroutebackend.place.infrastructure.persistence.entity.PlaceEntity;

public class PlaceEntityMapper {

    private PlaceEntityMapper() {}

    public static Place toDomain(PlaceEntity entity) {
        Place place = Place.builder()
                .id(PlaceId.of(entity.getId()))
                .name(entity.getName())
                .nameEn(entity.getNameEn())
                .nameTm(entity.getNameTm())
                .description(entity.getDescription())
                .address(entity.getAddress())
                .category(PlaceCategory.valueOf(entity.getCategory()))
                .cityId(entity.getCityId())
                .latitude(entity.getLatitude())
                .longitude(entity.getLongitude())
                .isActive(entity.getIsActive())
                .build();

        place.setCreatedAt(entity.getCreatedAt());
        place.setUpdatedAt(entity.getUpdatedAt());
        place.setVersion(entity.getVersion());

        return place;
    }

    public static PlaceEntity toEntity(Place place) {
        return PlaceEntity.builder()
                .id(place.getId().getValue())
                .name(place.getName())
                .nameEn(place.getNameEn())
                .nameTm(place.getNameTm())
                .description(place.getDescription())
                .address(place.getAddress())
                .category(place.getCategory().name())
                .cityId(place.getCityId())
                .latitude(place.getLatitude())
                .longitude(place.getLongitude())
                .isActive(place.getIsActive())
                .createdAt(place.getCreatedAt())
                .updatedAt(place.getUpdatedAt())
                .version(place.getVersion())
                .build();
    }
}
