package biz.ugur.busroutebackend.advertising.domain.model;

import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public record AdClickEvent(
        UUID id,
        PlacementId placementId,
        Instant occurredAt,
        TargetType targetType,
        UUID targetId
) {
    public static AdClickEvent newRecord(PlacementId placementId,
                                         TargetType targetType,
                                         UUID targetId,
                                         Clock clock) {
        return new AdClickEvent(
                UUID.randomUUID(),
                placementId,
                clock.instant(),
                targetType,
                targetId
        );
    }
}
