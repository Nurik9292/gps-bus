package biz.ugur.busroutebackend.place.application.dto;

public record PlaceSearchQuery(
        String query,
        String cityId,
        String category,
        int limit,
        String lang
) {}
