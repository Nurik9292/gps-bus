package biz.ugur.busroutebackend.transport.infrastructure.monitoring.offroute;

import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OffRouteStateRegistryTest {

    private final OffRouteStateRegistry registry = new OffRouteStateRegistry();
    private final LocalDate today = LocalDate.of(2026, 6, 9);

    @Test
    void recordsAndFindsByVehicleDateShift() {
        OffRouteRecord rec = new OffRouteRecord(Instant.parse("2026-06-09T08:00:00Z"), 250.0, 37.9, 58.3);
        registry.record("v1", today, ShiftType.FIRST, rec);

        Optional<OffRouteRecord> found = registry.find("v1", today, ShiftType.FIRST);
        assertTrue(found.isPresent());
        assertEquals(250.0, found.get().distanceMeters());
        assertTrue(registry.find("v1", today, ShiftType.SECOND).isEmpty());
    }

    @Test
    void recordIsStickyWithinShift() {
        OffRouteRecord first = new OffRouteRecord(Instant.parse("2026-06-09T08:00:00Z"), 250.0, 0, 0);
        OffRouteRecord second = new OffRouteRecord(Instant.parse("2026-06-09T09:00:00Z"), 999.0, 0, 0);
        registry.record("v1", today, ShiftType.FIRST, first);
        registry.record("v1", today, ShiftType.FIRST, second);

        assertEquals(250.0, registry.find("v1", today, ShiftType.FIRST).orElseThrow().distanceMeters());
    }

    @Test
    void cleanupRemovesOlderThanCutoff() {
        registry.record("v1", LocalDate.of(2026, 6, 7), ShiftType.FIRST,
                new OffRouteRecord(Instant.parse("2026-06-07T08:00:00Z"), 1, 0, 0));
        registry.record("v2", today, ShiftType.FIRST,
                new OffRouteRecord(Instant.parse("2026-06-09T08:00:00Z"), 1, 0, 0));

        registry.cleanupBefore(LocalDate.of(2026, 6, 9));

        assertTrue(registry.find("v1", LocalDate.of(2026, 6, 7), ShiftType.FIRST).isEmpty());
        assertTrue(registry.find("v2", today, ShiftType.FIRST).isPresent());
    }
}
