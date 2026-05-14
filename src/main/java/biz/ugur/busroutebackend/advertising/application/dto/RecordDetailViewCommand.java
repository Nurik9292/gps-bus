package biz.ugur.busroutebackend.advertising.application.dto;

import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;

import java.util.UUID;

public record RecordDetailViewCommand(
        String placementId,
        int durationMs,
        TargetType targetType,
        UUID targetId
) {}
