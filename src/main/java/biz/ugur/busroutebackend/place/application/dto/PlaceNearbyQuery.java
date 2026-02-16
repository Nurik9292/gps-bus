package biz.ugur.busroutebackend.place.application.dto;

public record PlaceNearbyQuery(
        double lat,
        double lng,
        int radiusMeters,
        String category,
        int limit
) {}
