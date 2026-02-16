package biz.ugur.busroutebackend.place.application.dto;

import java.math.BigDecimal;

public record GeocodingResult(
        String displayName,
        String address,
        String houseNumber,
        String street,
        String city,
        BigDecimal latitude,
        BigDecimal longitude,
        String source
) {}
