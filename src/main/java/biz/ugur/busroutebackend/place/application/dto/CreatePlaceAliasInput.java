package biz.ugur.busroutebackend.place.application.dto;

public record CreatePlaceAliasInput(
        String placeId,
        String alias,
        String language
) {}
