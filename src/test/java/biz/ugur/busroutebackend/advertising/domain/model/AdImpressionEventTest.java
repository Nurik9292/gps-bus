package biz.ugur.busroutebackend.advertising.domain.model;

import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AdImpressionEventTest {

    @Test
    void newRecord_generatesUuid_andUsesClockForOccurredAt() {
        PlacementId placementId = PlacementId.of(UUID.randomUUID().toString());
        Instant fixed = Instant.parse("2026-05-14T10:30:00Z");
        Clock clock = Clock.fixed(fixed, ZoneOffset.UTC);

        AdImpressionEvent event = AdImpressionEvent.newRecord(placementId, TargetType.HOME, null, clock);

        assertNotNull(event.id());
        assertEquals(placementId, event.placementId());
        assertEquals(fixed, event.occurredAt());
        assertEquals(TargetType.HOME, event.targetType());
        assertNull(event.targetId());
    }

    @Test
    void newRecord_allowsNullTargetType_andNullTargetId() {
        PlacementId placementId = PlacementId.of(UUID.randomUUID().toString());
        Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);

        AdImpressionEvent event = AdImpressionEvent.newRecord(placementId, null, null, clock);

        assertNull(event.targetType());
        assertNull(event.targetId());
    }

    @Test
    void newRecord_storesTargetIdWhenProvided() {
        PlacementId placementId = PlacementId.of(UUID.randomUUID().toString());
        UUID targetId = UUID.randomUUID();
        Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);

        AdImpressionEvent event = AdImpressionEvent.newRecord(placementId, TargetType.ROUTE, targetId, clock);

        assertEquals(TargetType.ROUTE, event.targetType());
        assertEquals(targetId, event.targetId());
    }
}
