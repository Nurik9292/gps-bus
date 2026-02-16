package biz.ugur.busroutebackend.place.application.dto;

import java.math.BigDecimal;

public record UpdatePlaceCommand(
        String id,
        String name,
        String nameEn,
        String nameTm,
        String description,
        String address,
        String category,
        String cityId,
        BigDecimal latitude,
        BigDecimal longitude
) {}
