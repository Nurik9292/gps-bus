package biz.ugur.busroutebackend.advertising.domain.model;

import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public record AdImpressionEvent(
        UUID id,
        PlacementId placementId,
        Instant occurredAt,
        TargetType targetType,
        UUID targetId
) {
    public static AdImpressionEvent newRecord(PlacementId placementId,
                                              TargetType targetType,
                                              UUID targetId,
                                              Clock clock) {
        return new AdImpressionEvent(
                UUID.randomUUID(),
                placementId,
                clock.instant(),
                targetType,
                targetId
        );
    }
}
