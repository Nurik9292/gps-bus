package biz.ugur.busroutebackend.place.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record PlaceSearchResult(
        String id,
        String name,
        String nameEn,
        String nameTm,
        String description,
        String address,
        String category,
        BigDecimal latitude,
        BigDecimal longitude,
        String cityId,
        List<String> aliases,
        Double similarityScore
) {}
