package biz.ugur.busroutebackend.transport.domain.specification;

import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class VehicleSpecificationsTest {

    private Vehicle active() {
        return Vehicle.create("d-1", "1234 AGH");
    }

    private Vehicle movingAssignedVehicle() {
        return active().toBuilder()
                .assignedRouteId(BusRouteId.generate())
                .routeNumber("29A")
                .isInMotion(true)
                .speedKmh(30.0)
                .currentLatitude(37.96)
                .currentLongitude(58.32)
                .lastPositionUpdate(LocalDateTime.now())
                .build();
    }

    @Nested
    class ActiveStateSpecs {

        @Test
        void isActiveMatchesActiveVehicle() {
            assertTrue(VehicleSpecifications.isActive().isSatisfiedBy(active()));
            assertFalse(VehicleSpecifications.isActive().isSatisfiedBy(active().deactivate()));
        }

        @Test
        void isInactiveMatchesDeactivatedVehicle() {
            assertTrue(VehicleSpecifications.isInactive().isSatisfiedBy(active().deactivate()));
            assertFalse(VehicleSpecifications.isInactive().isSatisfiedBy(active()));
        }
    }

    @Nested
    class Motion {

        @Test
        void isInMotionTrueWhenSpeedAboveOne() {
            assertTrue(VehicleSpecifications.isInMotion().isSatisfiedBy(movingAssignedVehicle()));
        }

        @Test
        void isInMotionFalseWhenSpeedBelowThreshold() {
            Vehicle slow = active().toBuilder()
                    .isInMotion(true).speedKmh(0.5).build();
            assertFalse(VehicleSpecifications.isInMotion().isSatisfiedBy(slow));
        }

        @Test
        void isStationaryTrueForNullSpeed() {
            assertTrue(VehicleSpecifications.isStationary().isSatisfiedBy(active()));
        }

        @Test
        void isStationaryFalseForMoving() {
            assertFalse(VehicleSpecifications.isStationary().isSatisfiedBy(movingAssignedVehicle()));
        }
    }

    @Nested
    class Position {

        @Test
        void hasPositionTrueWhenBothCoordinatesSet() {
            assertTrue(VehicleSpecifications.hasPosition().isSatisfiedBy(movingAssignedVehicle()));
        }

        @Test
        void hasPositionFalseWhenLatMissing() {
            assertFalse(VehicleSpecifications.hasPosition().isSatisfiedBy(active()));
        }

        @Test
        void hasRecentPositionForJustUpdatedVehicle() {
            assertTrue(VehicleSpecifications.hasRecentPosition().isSatisfiedBy(movingAssignedVehicle()));
        }

        @Test
        void hasRecentPositionFalseWhenStale() {
            Vehicle stale = active().toBuilder()
                    .lastPositionUpdate(LocalDateTime.now().minusHours(1)).build();
            assertFalse(VehicleSpecifications.hasRecentPosition().isSatisfiedBy(stale));
        }

        @Test
        void hasStalePositionTrueWhenOlderThanThreshold() {
            Vehicle stale = active().toBuilder()
                    .lastPositionUpdate(LocalDateTime.now().minusHours(1)).build();
            assertTrue(VehicleSpecifications.hasStalePosition(10).isSatisfiedBy(stale));
        }

        @Test
        void hasStalePositionTrueForNullTimestamp() {
            Vehicle noUpdate = active().toBuilder()
                    .lastPositionUpdate(null).build();
            assertTrue(VehicleSpecifications.hasStalePosition(10).isSatisfiedBy(noUpdate));
        }

        @Test
        void hasStalePositionFalseForFresh() {
            assertFalse(VehicleSpecifications.hasStalePosition(5).isSatisfiedBy(movingAssignedVehicle()));
        }
    }

    @Nested
    class Assignment {

        @Test
        void isAssignedTrueWhenRouteIdAndNumberSet() {
            assertTrue(VehicleSpecifications.isAssigned().isSatisfiedBy(movingAssignedVehicle()));
        }

        @Test
        void isUnassignedTrueForFreshVehicle() {
            assertTrue(VehicleSpecifications.isUnassigned().isSatisfiedBy(active()));
        }

        @Test
        void isUnassignedFalseForAssigned() {
            assertFalse(VehicleSpecifications.isUnassigned().isSatisfiedBy(movingAssignedVehicle()));
        }

        @Test
        void hasRouteNumberMatchesExact() {
            assertTrue(VehicleSpecifications.hasRouteNumber("29A").isSatisfiedBy(movingAssignedVehicle()));
            assertFalse(VehicleSpecifications.hasRouteNumber("7").isSatisfiedBy(movingAssignedVehicle()));
            assertFalse(VehicleSpecifications.hasRouteNumber(null).isSatisfiedBy(movingAssignedVehicle()));
        }
    }

    @Nested
    class Identity {

        @Test
        void hasDeviceIdMatchesExactValue() {
            assertTrue(VehicleSpecifications.hasDeviceId("d-1").isSatisfiedBy(active()));
            assertFalse(VehicleSpecifications.hasDeviceId("other").isSatisfiedBy(active()));
            assertFalse(VehicleSpecifications.hasDeviceId(null).isSatisfiedBy(active()));
        }

        @Test
        void hasLicensePlateMatchesExactValue() {
            assertTrue(VehicleSpecifications.hasLicensePlate("1234 AGH").isSatisfiedBy(active()));
            assertFalse(VehicleSpecifications.hasLicensePlate("9999 XYZ").isSatisfiedBy(active()));
        }
    }

    @Nested
    class Speed {

        @Test
        void speedAboveThreshold() {
            Vehicle fast = active().toBuilder().speedKmh(50.0).build();
            assertTrue(VehicleSpecifications.speedAbove(40.0).isSatisfiedBy(fast));
            assertFalse(VehicleSpecifications.speedAbove(60.0).isSatisfiedBy(fast));
        }

        @Test
        void speedBelowThreshold() {
            Vehicle slow = active().toBuilder().speedKmh(15.0).build();
            assertTrue(VehicleSpecifications.speedBelow(20.0).isSatisfiedBy(slow));
            assertFalse(VehicleSpecifications.speedBelow(10.0).isSatisfiedBy(slow));
        }

        @Test
        void speedAboveRejectsNullSpeed() {
            assertFalse(VehicleSpecifications.speedAbove(1.0).isSatisfiedBy(active().toBuilder().speedKmh(null).build()));
        }
    }

    @Nested
    class Composite {

        @Test
        void isReadyForServiceRequiresAllAspects() {
            assertTrue(VehicleSpecifications.isReadyForService().isSatisfiedBy(movingAssignedVehicle()));
            assertFalse(VehicleSpecifications.isReadyForService().isSatisfiedBy(active()));
        }

        @Test
        void isOperationalRequiresActivePositionAndRecency() {
            Vehicle locatedButUnassigned = active().toBuilder()
                    .currentLatitude(37.96).currentLongitude(58.32)
                    .lastPositionUpdate(LocalDateTime.now()).build();
            assertTrue(VehicleSpecifications.isOperational().isSatisfiedBy(locatedButUnassigned));
        }

        @Test
        void requiresAttentionForStaleOrUnassignedActive() {
            assertTrue(VehicleSpecifications.requiresAttention().isSatisfiedBy(active()));
            Vehicle staleAssigned = movingAssignedVehicle().toBuilder()
                    .lastPositionUpdate(LocalDateTime.now().minusHours(1)).build();
            assertTrue(VehicleSpecifications.requiresAttention().isSatisfiedBy(staleAssigned));
        }

        @Test
        void isActivelyWorkingRequiresMotion() {
            assertTrue(VehicleSpecifications.isActivelyWorking().isSatisfiedBy(movingAssignedVehicle()));
            Vehicle idleAssigned = movingAssignedVehicle().toBuilder()
                    .isInMotion(false).speedKmh(0.0).build();
            assertFalse(VehicleSpecifications.isActivelyWorking().isSatisfiedBy(idleAssigned));
        }
    }
}
