package biz.ugur.busroutebackend.place.application.dto;

import biz.ugur.busroutebackend.place.domain.model.StreetAlias;

public record StreetAliasResult(
        String id,
        String alias,
        String language
) {

    public static StreetAliasResult fromDomain(StreetAlias streetAlias) {
        return new StreetAliasResult(
                streetAlias.getId().getValue(),
                streetAlias.getAlias(),
                streetAlias.getLanguage()
        );
    }
}
