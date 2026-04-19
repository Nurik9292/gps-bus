package biz.ugur.busroutebackend.transport.domain.model;

import biz.ugur.busroutebackend.transport.domain.event.VehicleAssignedToRouteEvent;
import biz.ugur.busroutebackend.transport.domain.event.VehiclePositionUpdatedEvent;
import biz.ugur.busroutebackend.transport.domain.event.VehicleRegisteredEvent;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsProviderType;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteSource;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class VehicleTest {

    private static final String DEVICE_ID = "device-001";
    private static final String LICENSE_PLATE = "1234 AGH";
    private static final double ASHGABAT_LAT = 37.9601;
    private static final double ASHGABAT_LON = 58.3261;

    private Vehicle newVehicle;

    @BeforeEach
    void setUp() {
        newVehicle = Vehicle.create(DEVICE_ID, LICENSE_PLATE);
    }

    @Nested
    class Create {

        @Test
        void shouldCreateWithDefaults() {
            assertNotNull(newVehicle.getId());
            assertEquals(DEVICE_ID, newVehicle.getDeviceId());
            assertEquals(LICENSE_PLATE, newVehicle.getLicensePlate());
            assertTrue(newVehicle.getIsActive());
            assertFalse(newVehicle.getIsInMotion());
            assertEquals(0.0, newVehicle.getSpeedKmh());
            assertEquals(0.0, newVehicle.getCourse());
            assertFalse(newVehicle.getIsInGarage());
            assertEquals(RouteSource.UNKNOWN, newVehicle.getRouteSource());
            assertEquals(0, newVehicle.getRouteConfidence());
            assertTrue(newVehicle.getGpsDetectionEnabled());
            assertEquals(GpsProviderType.defaultProvider(), newVehicle.getGpsProvider());
            assertEquals(0L, newVehicle.getVersion());
        }

        @Test
        void shouldRegisterVehicleRegisteredEvent() {
            assertEquals(1, newVehicle.getDomainEvents().size());
            assertInstanceOf(VehicleRegisteredEvent.class, newVehicle.getDomainEvents().get(0));
        }

        @Test
        void shouldTrimAndUppercaseLicensePlate() {
            Vehicle vehicle = Vehicle.create(DEVICE_ID, "  1234 agh  ");
            assertEquals("1234 AGH", vehicle.getLicensePlate());
        }

        @Test
        void shouldRejectNullDeviceId() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> Vehicle.create(null, LICENSE_PLATE));
            assertTrue(ex.getMessage().contains("Device ID"));
        }

        @Test
        void shouldRejectEmptyDeviceId() {
            assertThrows(IllegalArgumentException.class,
                    () -> Vehicle.create("  ", LICENSE_PLATE));
        }

        @Test
        void shouldRejectInvalidLicensePlateFormat() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> Vehicle.create(DEVICE_ID, "INVALID"));
            assertTrue(ex.getMessage().contains("license plate"));
        }

        @Test
        void shouldUseProvidedGpsProvider() {
            Vehicle vehicle = Vehicle.create(DEVICE_ID, LICENSE_PLATE, GpsProviderType.TUGDK);
            assertEquals(GpsProviderType.TUGDK, vehicle.getGpsProvider());
        }

        @Test
        void shouldFallBackToDefaultProviderWhenNull() {
            Vehicle vehicle = Vehicle.create(DEVICE_ID, LICENSE_PLATE, null);
            assertEquals(GpsProviderType.defaultProvider(), vehicle.getGpsProvider());
        }
    }

    @Nested
    class UpdatePosition {

        @Test
        void shouldUpdateCoordinatesSpeedAndMotionFlag() {
            LocalDateTime fixTime = LocalDateTime.now();
            Vehicle moved = newVehicle.updatePosition(ASHGABAT_LAT, ASHGABAT_LON, 45.0, fixTime, 90.0);

            assertEquals(ASHGABAT_LAT, moved.getCurrentLatitude());
            assertEquals(ASHGABAT_LON, moved.getCurrentLongitude());
            assertEquals(45.0, moved.getSpeedKmh());
            assertTrue(moved.getIsInMotion());
            assertEquals(fixTime, moved.getLastPositionUpdate());
            assertEquals(90.0, moved.getCourse());
        }

        @Test
        void shouldNotMarkAsInMotionBelowThreshold() {
            Vehicle stationary = newVehicle.updatePosition(ASHGABAT_LAT, ASHGABAT_LON, 0.5, LocalDateTime.now(), 0.0);
            assertFalse(stationary.getIsInMotion());
        }

        @Test
        void shouldClampNegativeSpeedToZero() {
            Vehicle clamped = newVehicle.updatePosition(ASHGABAT_LAT, ASHGABAT_LON, -10.0, LocalDateTime.now(), 0.0);
            assertEquals(0.0, clamped.getSpeedKmh());
            assertFalse(clamped.getIsInMotion());
        }

        @Test
        void shouldClampExcessiveSpeedToMax() {
            Vehicle clamped = newVehicle.updatePosition(ASHGABAT_LAT, ASHGABAT_LON, 500.0, LocalDateTime.now(), 0.0);
            assertEquals(200.0, clamped.getSpeedKmh());
            assertTrue(clamped.getIsInMotion());
        }

        @Test
        void shouldHandleNullSpeedAndCourse() {
            Vehicle updated = newVehicle.updatePosition(ASHGABAT_LAT, ASHGABAT_LON, null, null, null);
            assertEquals(0.0, updated.getSpeedKmh());
            assertEquals(0.0, updated.getCourse());
            assertNotNull(updated.getLastPositionUpdate());
        }

        @Test
        void shouldRegisterPositionUpdatedEventOnResult() {
            Vehicle moved = newVehicle.updatePosition(ASHGABAT_LAT, ASHGABAT_LON, 30.0, LocalDateTime.now(), 180.0);
            assertEquals(1, moved.getDomainEvents().size());
            assertInstanceOf(VehiclePositionUpdatedEvent.class, moved.getDomainEvents().get(0));
        }

        @Test
        void shouldRejectCoordinatesOutsideTurkmenistan() {
            assertThrows(IllegalArgumentException.class,
                    () -> newVehicle.updatePosition(0.0, 0.0, 30.0, LocalDateTime.now(), 0.0));
        }

        @Test
        void shouldRejectNullCoordinates() {
            assertThrows(IllegalArgumentException.class,
                    () -> newVehicle.updatePosition(null, ASHGABAT_LON, 10.0, LocalDateTime.now(), 0.0));
        }

        @Test
        void shouldPreserveImmutabilityOfOriginal() {
            Vehicle moved = newVehicle.updatePosition(ASHGABAT_LAT, ASHGABAT_LON, 30.0, LocalDateTime.now(), 0.0);
            assertNull(newVehicle.getCurrentLatitude());
            assertNotSame(newVehicle, moved);
        }
    }

    @Nested
    class Direction {

        @Test
        void shouldReturnSameInstanceOnNullInputs() {
            Vehicle result = newVehicle.updateDirection(null, null);
            assertSame(newVehicle, result);
        }

        @Test
        void shouldSetDirectionAndLastStopSequence() {
            Vehicle updated = newVehicle.updateDirection(5, 0);
            assertEquals(5, updated.getLastStopSequence());
            assertEquals(0, updated.getCurrentDirection());
        }

        @Test
        void shouldChangeDirection() {
            Vehicle forward = newVehicle.updateDirection(5, 0);
            Vehicle backward = forward.updateDirection(6, 1);
            assertEquals(1, backward.getCurrentDirection());
        }
    }

    @Nested
    class RouteAssignment {

        @Test
        void clearRouteAssignmentIsNoOpWhenUnassigned() {
            Vehicle cleared = newVehicle.clearRouteAssignment();
            assertSame(newVehicle, cleared);
        }

        @Test
        void clearRouteAssignmentResetsRouteAndEmitsEvent() {
            Vehicle assigned = newVehicle.toBuilder()
                    .assignedRouteId(BusRouteId.generate())
                    .routeNumber("11")
                    .routeSource(RouteSource.GPS_DETECTED)
                    .routeConfidence(85)
                    .build();

            Vehicle cleared = assigned.clearRouteAssignment();

            assertNull(cleared.getAssignedRouteId());
            assertNull(cleared.getRouteNumber());
            assertEquals(RouteSource.UNKNOWN, cleared.getRouteSource());
            assertEquals(0, cleared.getRouteConfidence());
            assertEquals(1, cleared.getDomainEvents().size());
            assertInstanceOf(VehicleAssignedToRouteEvent.class, cleared.getDomainEvents().get(0));
        }

        @Test
        void updateCachedRouteNumberChangesOnlyRouteNumber() {
            Vehicle updated = newVehicle.updateCachedRouteNumber("42");
            assertEquals("42", updated.getRouteNumber());
            assertNull(updated.getAssignedRouteId());
        }

        @Test
        void hasAssignedRouteDetectsAssignment() {
            assertFalse(newVehicle.hasAssignedRoute());
            Vehicle assigned = newVehicle.toBuilder()
                    .assignedRouteId(BusRouteId.generate())
                    .routeNumber("11")
                    .build();
            assertTrue(assigned.hasAssignedRoute());
        }

        @Test
        void hasAssignedRouteRequiresBothIdAndNumber() {
            Vehicle partial = newVehicle.toBuilder()
                    .assignedRouteId(BusRouteId.generate())
                    .routeNumber("")
                    .build();
            assertFalse(partial.hasAssignedRoute());
        }
    }

    @Nested
    class ActivationLifecycle {

        @Test
        void deactivateClearsRouteAndMotion() {
            Vehicle assigned = newVehicle.toBuilder()
                    .assignedRouteId(BusRouteId.generate())
                    .routeNumber("11")
                    .isInMotion(true)
                    .build();

            Vehicle deactivated = assigned.deactivate();

            assertFalse(deactivated.getIsActive());
            assertFalse(deactivated.getIsInMotion());
            assertNull(deactivated.getAssignedRouteId());
            assertNull(deactivated.getRouteNumber());
        }

        @Test
        void deactivateIsIdempotent() {
            Vehicle offline = newVehicle.deactivate();
            Vehicle againOffline = offline.deactivate();
            assertSame(offline, againOffline);
        }

        @Test
        void activateFlipsFlagWhenInactive() {
            Vehicle offline = newVehicle.deactivate();
            Vehicle reactivated = offline.activate();
            assertTrue(reactivated.getIsActive());
        }

        @Test
        void activateIsIdempotent() {
            Vehicle again = newVehicle.activate();
            assertSame(newVehicle, again);
        }
    }

    @Nested
    class Garage {

        @Test
        void enterGarageRecordsIdAndStopsMotion() {
            Vehicle moving = newVehicle.updatePosition(ASHGABAT_LAT, ASHGABAT_LON, 30.0, LocalDateTime.now(), 0.0);
            Vehicle parked = moving.enterGarage("garage-1");
            assertTrue(parked.isCurrentlyInGarage());
            assertEquals("garage-1", parked.getLastGarageId());
            assertNotNull(parked.getGarageEntryTime());
            assertNull(parked.getGarageExitTime());
            assertFalse(parked.getIsInMotion());
        }

        @Test
        void enterGarageRejectsBlankId() {
            assertThrows(IllegalArgumentException.class, () -> newVehicle.enterGarage(" "));
            assertThrows(IllegalArgumentException.class, () -> newVehicle.enterGarage(null));
        }

        @Test
        void exitGarageRecordsExitTime() {
            Vehicle parked = newVehicle.enterGarage("garage-1");
            Vehicle departed = parked.exitGarage();
            assertFalse(departed.isCurrentlyInGarage());
            assertNotNull(departed.getGarageExitTime());
            assertEquals("garage-1", departed.getLastGarageId());
        }

        @Test
        void exitGarageNoOpWhenNotInGarage() {
            Vehicle result = newVehicle.exitGarage();
            assertSame(newVehicle, result);
        }
    }

    @Nested
    class Queries {

        @Test
        void hasPositionReturnsFalseWhenCoordinatesMissing() {
            assertFalse(newVehicle.hasPosition());
            assertNull(newVehicle.toCoordinates());
        }

        @Test
        void hasPositionReturnsTrueAfterUpdate() {
            Vehicle located = newVehicle.updatePosition(ASHGABAT_LAT, ASHGABAT_LON, 0.0, LocalDateTime.now(), 0.0);
            assertTrue(located.hasPosition());
            assertNotNull(located.toCoordinates());
            assertEquals(ASHGABAT_LAT, located.toCoordinates().getLatitude().doubleValue(), 1e-6);
            assertEquals(ASHGABAT_LON, located.toCoordinates().getLongitude().doubleValue(), 1e-6);
        }

        @Test
        void hasRecentPositionTrueImmediatelyAfterCreate() {
            assertTrue(newVehicle.hasRecentPosition(120));
        }

        @Test
        void hasRecentPositionTrueForRecentLastPositionUpdate() {
            Vehicle moved = newVehicle.updatePosition(
                    ASHGABAT_LAT, ASHGABAT_LON, 5.0, LocalDateTime.now(), 0.0
            );
            assertTrue(moved.hasRecentPosition(120));
        }

        @Test
        void hasRecentPositionFalseForStaleLastPositionUpdate() {
            Vehicle stale = newVehicle.updatePosition(
                    ASHGABAT_LAT, ASHGABAT_LON, 5.0,
                    LocalDateTime.now().minusMinutes(10),
                    0.0
            );
            assertFalse(stale.hasRecentPosition(60));
        }
    }

    @Nested
    class Restore {

        @Test
        void restoreRebuildsStateWithoutEvents() {
            Vehicle created = Vehicle.create(DEVICE_ID, LICENSE_PLATE);
            Vehicle restored = Vehicle.restore(
                    created.getId(),
                    DEVICE_ID,
                    LICENSE_PLATE,
                    ASHGABAT_LAT, ASHGABAT_LON,
                    40.0, true, LocalDateTime.now(),
                    BusRouteId.generate(), "11",
                    true, 90.0, 0, 5,
                    null, null, null, false,
                    RouteSource.GPS_DETECTED, 80, true,
                    GpsProviderType.TUGDK,
                    LocalDateTime.now().minusDays(1), LocalDateTime.now(),
                    3L
            );
            assertEquals(created.getId(), restored.getId());
            assertEquals(40.0, restored.getSpeedKmh());
            assertEquals(3L, restored.getVersion());
            assertEquals(0, restored.getDomainEvents().size());
        }

        @Test
        void restoreAppliesDefaultsForNullFlags() {
            Vehicle restored = Vehicle.restore(
                    VehicleId.generate(),
                    DEVICE_ID, LICENSE_PLATE,
                    null, null,
                    null, null, null,
                    null, null,
                    null, null, null, null,
                    null, null, null, null,
                    null, null, null,
                    null,
                    null, null, null
            );
            assertEquals(0.0, restored.getSpeedKmh());
            assertFalse(restored.getIsInMotion());
            assertTrue(restored.getIsActive());
            assertFalse(restored.getIsInGarage());
            assertEquals(RouteSource.UNKNOWN, restored.getRouteSource());
            assertEquals(0, restored.getRouteConfidence());
            assertTrue(restored.getGpsDetectionEnabled());
            assertEquals(GpsProviderType.defaultProvider(), restored.getGpsProvider());
            assertEquals(0L, restored.getVersion());
        }
    }

    @Test
    void updateDeviceIdValidatesAndTrims() {
        Vehicle updated = newVehicle.updateDeviceId("  new-device  ");
        assertEquals("new-device", updated.getDeviceId());
    }

    @Test
    void toStringContainsLicensePlateAndRoutePlaceholder() {
        String s = newVehicle.toString();
        assertTrue(s.contains(LICENSE_PLATE));
        assertTrue(s.contains("UNASSIGNED"));
    }
}
