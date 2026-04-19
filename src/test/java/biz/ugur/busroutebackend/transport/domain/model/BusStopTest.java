package biz.ugur.busroutebackend.transport.domain.model;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.transport.domain.event.BusStopCreatedEvent;
import biz.ugur.busroutebackend.transport.domain.event.BusStopLocationChangedEvent;
import biz.ugur.busroutebackend.transport.domain.event.BusStopNameChangedEvent;
import biz.ugur.busroutebackend.transport.domain.event.BusStopRegisteredEvent;
import biz.ugur.busroutebackend.transport.domain.event.BusStopUpdatedEvent;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BusStopTest {

    private static final BigDecimal LAT = new BigDecimal("37.9601");
    private static final BigDecimal LON = new BigDecimal("58.3261");

    private BusStop fresh;
    private StopCode code;

    @BeforeEach
    void setUp() {
        code = StopCode.of("ASH001");
        fresh = BusStop.create(
                "Berkararlyk",
                "Berkararlyk",
                "Berkararlyk",
                code,
                LAT, LON,
                true,
                "city-001",
                "admin"
        );
    }

    @Nested
    class Create {

        @Test
        void shouldCreateActiveStopWithAllFields() {
            assertNotNull(fresh.getId());
            assertEquals("Berkararlyk", fresh.getStopName());
            assertEquals(code, fresh.getStopCode());
            assertEquals(LAT, fresh.getLatitude());
            assertEquals(LON, fresh.getLongitude());
            assertTrue(fresh.getIsActive());
            assertTrue(fresh.getIsMajorStop());
            assertEquals("city-001", fresh.getCityId());
            assertEquals(0L, fresh.getVersion());
        }

        @Test
        void shouldEmitCreatedAndRegisteredEvents() {
            List<?> events = fresh.getDomainEvents();
            assertEquals(2, events.size());
            assertInstanceOf(BusStopCreatedEvent.class, events.get(0));
            assertInstanceOf(BusStopRegisteredEvent.class, events.get(1));
        }

        @Test
        void shouldPassCreatedByToRegisteredEvent() {
            BusStopRegisteredEvent registered = (BusStopRegisteredEvent) fresh.getDomainEvents().get(1);
            assertEquals("admin", registered.createdBy());
        }

        @Test
        void shouldFallBackToAnonymousWhenCreatedByBlank() {
            BusStop stop = BusStop.create("Stop", "S", "S", code, LAT, LON, false, "c", "   ");
            BusStopRegisteredEvent registered = (BusStopRegisteredEvent) stop.getDomainEvents().get(1);
            assertEquals("anonymous", registered.createdBy());
        }

        @Test
        void shouldFallBackToAnonymousWhenCreatedByNull() {
            BusStop stop = BusStop.create("Stop", "S", "S", code, LAT, LON, false, "c", null);
            BusStopRegisteredEvent registered = (BusStopRegisteredEvent) stop.getDomainEvents().get(1);
            assertEquals("anonymous", registered.createdBy());
        }

        @Test
        void shouldDefaultIsMajorStopToFalseWhenNull() {
            BusStop stop = BusStop.create("Stop", "S", "S", code, LAT, LON, null, "c", "admin");
            assertFalse(stop.getIsMajorStop());
        }

        @Test
        void shouldRejectBlankStopName() {
            assertThrows(IllegalArgumentException.class,
                    () -> BusStop.create(" ", "S", "S", code, LAT, LON, false, "c", "admin"));
        }
    }

    @Nested
    class UpdateInfo {

        @Test
        void updateInfoRecordsNameAndLocationEventsSeparately() {
            BigDecimal newLat = new BigDecimal("38.0000");
            BigDecimal newLon = new BigDecimal("58.4000");

            BusStop updated = fresh.updateInfo(
                    "New Name", "New Name EN", "New Name TM",
                    newLat, newLon, true, true, "city-001"
            );

            List<?> events = updated.getDomainEvents();
            boolean hasLocation = events.stream().anyMatch(e -> e instanceof BusStopLocationChangedEvent);
            boolean hasName = events.stream().anyMatch(e -> e instanceof BusStopNameChangedEvent);
            boolean hasUpdated = events.stream().anyMatch(e -> e instanceof BusStopUpdatedEvent);
            assertTrue(hasLocation);
            assertTrue(hasName);
            assertTrue(hasUpdated);
            assertEquals("New Name", updated.getStopName());
            assertEquals(newLat, updated.getLatitude());
        }

        @Test
        void updateInfoWithSameDataStillEmitsUpdatedEvent() {
            BusStop updated = fresh.updateInfo(
                    "Berkararlyk", "Berkararlyk", "Berkararlyk",
                    LAT, LON, true, true, "city-001"
            );
            boolean hasLocation = updated.getDomainEvents().stream()
                    .anyMatch(e -> e instanceof BusStopLocationChangedEvent);
            boolean hasName = updated.getDomainEvents().stream()
                    .anyMatch(e -> e instanceof BusStopNameChangedEvent);
            assertFalse(hasLocation);
            assertFalse(hasName);
            assertTrue(updated.getDomainEvents().stream()
                    .anyMatch(e -> e instanceof BusStopUpdatedEvent));
        }

        @Test
        void updateLocationFromCoordinatesRequiresCoordinates() {
            assertThrows(IllegalArgumentException.class,
                    () -> fresh.updateLocationFromCoordinates(null));
        }

        @Test
        void updateLocationFromCoordinatesEmitsEventOnChange() {
            Coordinates moved = Coordinates.of(new BigDecimal("38.5000"), new BigDecimal("58.5000"));
            BusStop updated = fresh.updateLocationFromCoordinates(moved);
            assertEquals(0, new BigDecimal("38.5").compareTo(updated.getLatitude()));
            assertTrue(updated.getDomainEvents().stream()
                    .anyMatch(e -> e instanceof BusStopLocationChangedEvent));
        }
    }

    @Nested
    class ActivationAndMajorStop {

        @Test
        void deactivateFlipsFlag() {
            BusStop off = fresh.deactivate();
            assertFalse(off.getIsActive());
        }

        @Test
        void activateFlipsBackOn() {
            BusStop off = fresh.deactivate();
            BusStop on = off.activate();
            assertTrue(on.getIsActive());
        }

        @Test
        void activationIdempotent() {
            assertSame(fresh, fresh.activate());
        }

        @Test
        void deactivationIdempotent() {
            BusStop off = fresh.deactivate();
            assertSame(off, off.deactivate());
        }

        @Test
        void markAsMajorStopIdempotentWhenAlreadyMajor() {
            assertSame(fresh, fresh.markAsMajorStop());
        }

        @Test
        void unmarkAsMajorStopFlipsFlag() {
            BusStop minor = fresh.unmarkAsMajorStop();
            assertFalse(minor.getIsMajorStop());
        }

        @Test
        void unmarkIsIdempotent() {
            BusStop minor = fresh.unmarkAsMajorStop();
            assertSame(minor, minor.unmarkAsMajorStop());
        }
    }

    @Nested
    class Queries {

        @Test
        void getServingRoutesCountReturnsFiveForMajorTwoForMinor() {
            assertEquals(5, fresh.getServingRoutesCount());
            BusStop minor = fresh.unmarkAsMajorStop();
            assertEquals(2, minor.getServingRoutesCount());
        }

        @Test
        void getDisplayNameHandlesLanguages() {
            assertEquals("Berkararlyk", fresh.getDisplayName("en"));
            assertEquals("Berkararlyk", fresh.getDisplayName("tm"));
            assertEquals("Berkararlyk", fresh.getDisplayName("ru"));
            assertEquals("Berkararlyk", fresh.getDisplayName(null));
        }

        @Test
        void hasTranslationHandlesLanguages() {
            assertTrue(fresh.hasTranslation("en"));
            assertTrue(fresh.hasTranslation("tm"));
            assertTrue(fresh.hasTranslation("ru"));
            assertFalse(fresh.hasTranslation("xx"));
        }

        @Test
        void hasCompleteTranslationsTrueWhenAllLanguagesPresent() {
            assertTrue(fresh.hasCompleteTranslations());
        }

        @Test
        void hasCompleteTranslationsFalseWhenEnMissing() {
            BusStop partial = fresh.toBuilder().nameEn(null).build();
            assertFalse(partial.hasCompleteTranslations());
        }

        @Test
        void hasLocationTrueWhenCoordinatesPresent() {
            assertTrue(fresh.hasLocation());
            assertNotNull(fresh.toCoordinates());
        }
    }

    @Test
    void restoreRebuildsStateWithoutEvents() {
        BusStop restored = BusStop.restore(
                fresh.getId(), "Berkararlyk", "Berkararlyk", "Berkararlyk",
                code, LAT, LON, true, true, "city-001",
                fresh.getCreatedAt(), fresh.getUpdatedAt(), 7L
        );
        assertEquals(fresh.getId(), restored.getId());
        assertEquals(7L, restored.getVersion());
        assertEquals(0, restored.getDomainEvents().size());
    }

    @Test
    void restoreDefaultsActiveAndMajorStopVersionWhenNull() {
        BusStop restored = BusStop.restore(
                BusStopId.generate(), "Stop", "", "", code, LAT, LON,
                null, null, "c", null, null, null
        );
        assertTrue(restored.getIsActive());
        assertFalse(restored.getIsMajorStop());
        assertEquals(0L, restored.getVersion());
    }
}
