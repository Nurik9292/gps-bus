package biz.ugur.busroutebackend.place.infrastructure.mapper;

import biz.ugur.busroutebackend.place.domain.model.StreetAlias;
import biz.ugur.busroutebackend.place.domain.valueobjects.StreetAliasId;
import biz.ugur.busroutebackend.place.infrastructure.persistence.entity.StreetAliasEntity;

public class StreetAliasEntityMapper {

    private StreetAliasEntityMapper() {}

    public static StreetAlias toDomain(StreetAliasEntity entity) {
        StreetAlias alias = StreetAlias.builder()
                .id(StreetAliasId.of(entity.getId()))
                .streetId(entity.getStreetId())
                .alias(entity.getAlias())
                .language(entity.getLanguage())
                .build();

        alias.setCreatedAt(entity.getCreatedAt());
        alias.setUpdatedAt(entity.getUpdatedAt());
        alias.setVersion(entity.getVersion());

        return alias;
    }

    public static StreetAliasEntity toEntity(StreetAlias alias) {
        return StreetAliasEntity.builder()
                .id(alias.getId().getValue())
                .streetId(alias.getStreetId())
                .alias(alias.getAlias())
                .language(alias.getLanguage())
                .createdAt(alias.getCreatedAt())
                .updatedAt(alias.getUpdatedAt())
                .version(alias.getVersion())
                .build();
    }
}
