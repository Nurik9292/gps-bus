package biz.ugur.busroutebackend.place.infrastructure.mapper;

import biz.ugur.busroutebackend.place.domain.model.PlaceAlias;
import biz.ugur.busroutebackend.place.domain.valueobjects.PlaceAliasId;
import biz.ugur.busroutebackend.place.infrastructure.persistence.entity.PlaceAliasEntity;

public class PlaceAliasEntityMapper {

    private PlaceAliasEntityMapper() {}

    public static PlaceAlias toDomain(PlaceAliasEntity entity) {
        PlaceAlias alias = PlaceAlias.builder()
                .id(PlaceAliasId.of(entity.getId()))
                .placeId(entity.getPlaceId())
                .alias(entity.getAlias())
                .language(entity.getLanguage())
                .build();

        alias.setCreatedAt(entity.getCreatedAt());
        alias.setUpdatedAt(entity.getUpdatedAt());
        alias.setVersion(entity.getVersion());

        return alias;
    }

    public static PlaceAliasEntity toEntity(PlaceAlias alias) {
        return PlaceAliasEntity.builder()
                .id(alias.getId().getValue())
                .placeId(alias.getPlaceId())
                .alias(alias.getAlias())
                .language(alias.getLanguage())
                .createdAt(alias.getCreatedAt())
                .updatedAt(alias.getUpdatedAt())
                .version(alias.getVersion())
                .build();
    }
}
