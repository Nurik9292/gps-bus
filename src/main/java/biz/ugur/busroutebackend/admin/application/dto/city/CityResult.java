package biz.ugur.busroutebackend.admin.application.dto.city;

import biz.ugur.busroutebackend.admin.domain.model.City;

public record CityResult(
        String id,
        String name,
        String nameTm,
        Boolean isActive,
        Integer displayOrder
) {

    public static CityResult fromDomain(City city) {
        return new CityResult(
                city.getId().getValue(),
                city.getName(),
                city.getNameTm(),
                city.getIsActive(),
                city.getDisplayOrder()
        );
    }
}
