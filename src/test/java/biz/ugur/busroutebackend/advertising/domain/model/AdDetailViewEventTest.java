package biz.ugur.busroutebackend.advertising.domain.model;

import biz.ugur.busroutebackend.advertising.domain.enums.TargetType;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AdDetailViewEventTest {

    @Test
    void newRecord_storesDurationMs() {
        PlacementId placementId = PlacementId.of(UUID.randomUUID().toString());
        Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);

        AdDetailViewEvent event = AdDetailViewEvent.newRecord(placementId, 12500, TargetType.PLACE, UUID.randomUUID(), clock);

        assertEquals(12500, event.durationMs());
    }

    @Test
    void newRecord_negativeDuration_throws() {
        PlacementId placementId = PlacementId.of(UUID.randomUUID().toString());
        Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AdDetailViewEvent.newRecord(placementId, -1, null, null, clock));
        assertTrue(ex.getMessage().contains("durationMs"));
    }

    @Test
    void newRecord_durationOverThirtyMinutes_throws() {
        PlacementId placementId = PlacementId.of(UUID.randomUUID().toString());
        Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);

        assertThrows(IllegalArgumentException.class,
                () -> AdDetailViewEvent.newRecord(placementId, 1_800_001, null, null, clock));
    }

    @Test
    void newRecord_durationAtUpperBound_isAccepted() {
        PlacementId placementId = PlacementId.of(UUID.randomUUID().toString());
        Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);

        AdDetailViewEvent event = AdDetailViewEvent.newRecord(placementId, 1_800_000, null, null, clock);

        assertEquals(1_800_000, event.durationMs());
    }
}
