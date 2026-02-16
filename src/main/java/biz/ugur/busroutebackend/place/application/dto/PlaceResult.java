package biz.ugur.busroutebackend.place.application.dto;

import biz.ugur.busroutebackend.place.domain.model.Place;

import java.math.BigDecimal;
import java.util.List;

public record PlaceResult(
        String id,
        String name,
        String nameEn,
        String nameTm,
        String description,
        String address,
        String category,
        String cityId,
        BigDecimal latitude,
        BigDecimal longitude,
        Boolean isActive,
        List<AliasResult> aliases
) {

    public static PlaceResult fromDomain(Place place) {
        return new PlaceResult(
                place.getId().getValue(),
                place.getName(),
                place.getNameEn(),
                place.getNameTm(),
                place.getDescription(),
                place.getAddress(),
                place.getCategory().name(),
                place.getCityId(),
                place.getLatitude(),
                place.getLongitude(),
                place.getIsActive(),
                List.of()
        );
    }

    public static PlaceResult fromDomain(Place place, List<AliasResult> aliases) {
        return new PlaceResult(
                place.getId().getValue(),
                place.getName(),
                place.getNameEn(),
                place.getNameTm(),
                place.getDescription(),
                place.getAddress(),
                place.getCategory().name(),
                place.getCityId(),
                place.getLatitude(),
                place.getLongitude(),
                place.getIsActive(),
                aliases
        );
    }
}
