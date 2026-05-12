package biz.ugur.busroutebackend.client.application.dto.city;

import biz.ugur.busroutebackend.admin.domain.model.City;

public record MobileCityResult(
        String id,
        String name,
        String nameTm,
        Integer displayOrder,
        Double latitude,
        Double longitude
) {

    public static MobileCityResult fromDomain(City city) {
        return new MobileCityResult(
                city.getId().getValue(),
                city.getName(),
                city.getNameTm(),
                city.getDisplayOrder(),
                city.getLatitude(),
                city.getLongitude()
        );
    }
}
