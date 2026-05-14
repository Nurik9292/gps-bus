package biz.ugur.busroutebackend.advertising.application.dto;

import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;

import java.util.UUID;

public record RecordClickCommand(
        String placementId,
        TargetType targetType,
        UUID targetId
) {}
