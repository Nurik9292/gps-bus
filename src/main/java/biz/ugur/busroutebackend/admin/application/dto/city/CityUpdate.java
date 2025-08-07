package biz.ugur.busroutebackend.admin.application.dto.city;

public record CityUpdate(
        String id,
        String name,
        String nameTm,
        Boolean isActive,
        Integer displayOrder
) {
}
