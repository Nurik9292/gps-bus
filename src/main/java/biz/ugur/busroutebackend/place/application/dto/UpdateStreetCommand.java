package biz.ugur.busroutebackend.place.application.dto;

public record UpdateStreetCommand(
        String id,
        String name,
        String nameEn,
        String nameTm,
        String cityId
) {}
