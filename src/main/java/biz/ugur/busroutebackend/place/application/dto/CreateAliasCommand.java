package biz.ugur.busroutebackend.place.application.dto;

public record CreateAliasCommand(
        String alias,
        String language
) {}
