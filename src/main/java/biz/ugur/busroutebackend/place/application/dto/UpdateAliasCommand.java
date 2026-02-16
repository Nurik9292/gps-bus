package biz.ugur.busroutebackend.place.application.dto;

public record UpdateAliasCommand(
        String id,
        String alias,
        String language
) {}
