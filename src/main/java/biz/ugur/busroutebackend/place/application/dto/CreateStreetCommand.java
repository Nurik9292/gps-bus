package biz.ugur.busroutebackend.place.application.dto;

public record CreateStreetCommand(
        String name,
        String nameEn,
        String nameTm,
        String cityId
) {}
