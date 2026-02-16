package biz.ugur.busroutebackend.place.application.dto;

import biz.ugur.busroutebackend.place.domain.model.Street;

import java.util.List;

public record StreetResult(
        String id,
        String name,
        String nameEn,
        String nameTm,
        String cityId,
        Boolean isActive,
        List<StreetAliasResult> aliases
) {

    public static StreetResult fromDomain(Street street) {
        return new StreetResult(
                street.getId().getValue(),
                street.getName(),
                street.getNameEn(),
                street.getNameTm(),
                street.getCityId(),
                street.getIsActive(),
                List.of()
        );
    }

    public static StreetResult fromDomain(Street street, List<StreetAliasResult> aliases) {
        return new StreetResult(
                street.getId().getValue(),
                street.getName(),
                street.getNameEn(),
                street.getNameTm(),
                street.getCityId(),
                street.getIsActive(),
                aliases
        );
    }
}
