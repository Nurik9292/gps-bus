package biz.ugur.busroutebackend.advertising.domain.model;

import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public record AdDetailViewEvent(
        UUID id,
        PlacementId placementId,
        Instant occurredAt,
        int durationMs,
        TargetType targetType,
        UUID targetId
) {
    private static final int MAX_DURATION_MS = 1_800_000;

    public static AdDetailViewEvent newRecord(PlacementId placementId,
                                              int durationMs,
                                              TargetType targetType,
                                              UUID targetId,
                                              Clock clock) {
        if (durationMs < 0 || durationMs > MAX_DURATION_MS) {
            throw new IllegalArgumentException(
                    "durationMs out of range [0, %d]: %d".formatted(MAX_DURATION_MS, durationMs));
        }
        return new AdDetailViewEvent(
                UUID.randomUUID(),
                placementId,
                clock.instant(),
                durationMs,
                targetType,
                targetId
        );
    }
}
