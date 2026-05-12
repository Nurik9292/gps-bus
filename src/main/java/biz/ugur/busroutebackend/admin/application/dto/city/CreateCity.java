package biz.ugur.busroutebackend.admin.application.dto.city;

public record CreateCity(
        String name,
        String nameTm,
        Boolean isActive,
        Integer displayOrder,
        Double latitude,
        Double longitude
) {

}
