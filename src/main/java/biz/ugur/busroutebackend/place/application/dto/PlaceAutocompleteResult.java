package biz.ugur.busroutebackend.place.application.dto;

public record PlaceAutocompleteResult(
        String id,
        String name,
        String category,
        String address
) {}
