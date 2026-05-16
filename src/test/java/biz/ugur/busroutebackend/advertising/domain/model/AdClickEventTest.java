package biz.ugur.busroutebackend.advertising.domain.model;

import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AdClickEventTest {

    @Test
    void newRecord_generatesUuid_andUsesClockForOccurredAt() {
        PlacementId placementId = PlacementId.of(UUID.randomUUID().toString());
        Instant fixed = Instant.parse("2026-05-14T10:30:00Z");
        Clock clock = Clock.fixed(fixed, ZoneOffset.UTC);

        AdClickEvent event = AdClickEvent.newRecord(placementId, TargetType.POPUP, null, clock);

        assertNotNull(event.id());
        assertEquals(placementId, event.placementId());
        assertEquals(fixed, event.occurredAt());
        assertEquals(TargetType.POPUP, event.targetType());
        assertNull(event.targetId());
    }

    @Test
    void newRecord_allowsNullTargetType_andNullTargetId() {
        PlacementId placementId = PlacementId.of(UUID.randomUUID().toString());
        Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);

        AdClickEvent event = AdClickEvent.newRecord(placementId, null, null, clock);

        assertNull(event.targetType());
        assertNull(event.targetId());
    }
}
