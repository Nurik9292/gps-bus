package biz.ugur.busroutebackend.place.application.dto;

import biz.ugur.busroutebackend.place.domain.model.PlaceAlias;

public record AliasResult(
        String id,
        String alias,
        String language
) {

    public static AliasResult fromDomain(PlaceAlias placeAlias) {
        return new AliasResult(
                placeAlias.getId().getValue(),
                placeAlias.getAlias(),
                placeAlias.getLanguage()
        );
    }
}
