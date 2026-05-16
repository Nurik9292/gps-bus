package biz.ugur.busroutebackend.advertising.application.dto;

import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;

import java.util.UUID;

public record RecordImpressionCommand(
        String placementId,
        TargetType targetType,
        UUID targetId
) {}
