package biz.ugur.busroutebackend.transport.domain.service;

import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VehicleValidationServiceTest {

    private VehicleValidationService service;

    @BeforeEach
    void setUp() {
        service = new VehicleValidationService();
    }

    @Nested
    class StaticValidators {

        @Test
        void validateDeviceIdTrimsAndReturns() {
            assertEquals("abc", VehicleValidationService.validateDeviceId("  abc  "));
        }

        @Test
        void validateDeviceIdRejectsNullOrBlank() {
            assertThrows(IllegalArgumentException.class,
                    () -> VehicleValidationService.validateDeviceId(null));
            assertThrows(IllegalArgumentException.class,
                    () -> VehicleValidationService.validateDeviceId("   "));
        }

        @Test
        void validateLicensePlateNormalisesCase() {
            assertEquals("1234 AGH", VehicleValidationService.validateLicensePlate("  1234 agh "));
        }

        @Test
        void validateLicensePlateRejectsInvalidFormat() {
            assertThrows(IllegalArgumentException.class,
                    () -> VehicleValidationService.validateLicensePlate("INVALID"));
            assertThrows(IllegalArgumentException.class,
                    () -> VehicleValidationService.validateLicensePlate(""));
            assertThrows(IllegalArgumentException.class,
                    () -> VehicleValidationService.validateLicensePlate("12345 AGH"));
        }

        @Test
        void validateCoordinatesAcceptsAshgabat() {
            assertDoesNotThrow(() -> VehicleValidationService.validateCoordinates(37.9601, 58.3261));
        }

        @Test
        void validateCoordinatesRejectsOutOfBounds() {
            assertThrows(IllegalArgumentException.class,
                    () -> VehicleValidationService.validateCoordinates(0.0, 0.0));
        }

        @Test
        void validateCoordinatesRejectsNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> VehicleValidationService.validateCoordinates(null, 58.3261));
            assertThrows(IllegalArgumentException.class,
                    () -> VehicleValidationService.validateCoordinates(37.9601, null));
        }

        @Test
        void isValidCoordinatesReturnsBooleanInsteadOfThrowing() {
            assertTrue(VehicleValidationService.isValidCoordinates(37.9601, 58.3261));
            assertFalse(VehicleValidationService.isValidCoordinates(0.0, 0.0));
            assertFalse(VehicleValidationService.isValidCoordinates(null, null));
        }
    }

    @Nested
    class LicensePlateFormat {

        @Test
        void acceptsCanonicalFormat() {
            assertTrue(service.isValidLicensePlateFormat("1234 AGH"));
            assertTrue(service.isValidLicensePlateFormat("  1992 ABC  "));
        }

        @Test
        void rejectsBadInputs() {
            assertFalse(service.isValidLicensePlateFormat(null));
            assertFalse(service.isValidLicensePlateFormat(""));
            assertFalse(service.isValidLicensePlateFormat("12345 AGH"));
            assertFalse(service.isValidLicensePlateFormat("ABCD 1234"));
        }
    }

    @Nested
    class GpsPositionValidation {

        @Test
        void nullPositionIsNullPosition() {
            assertEquals(GpsValidationResult.NULL_POSITION, service.validateGpsPosition(null));
            assertFalse(service.isValidGpsPosition(null));
        }

        @Test
        void missingDeviceIdReportsInvalidDeviceId() {
            GpsPositionDTO pos = new GpsPositionDTO();
            pos.setDeviceId("  ");
            pos.setLatitude(37.9601);
            pos.setLongitude(58.3261);
            assertEquals(GpsValidationResult.INVALID_DEVICE_ID, service.validateGpsPosition(pos));
        }

        @Test
        void nullCoordinatesReportNullCoordinates() {
            GpsPositionDTO pos = new GpsPositionDTO();
            pos.setDeviceId("d-1");
            pos.setLatitude(null);
            pos.setLongitude(58.3261);
            assertEquals(GpsValidationResult.NULL_COORDINATES, service.validateGpsPosition(pos));
        }

        @Test
        void outOfBoundsCoordinatesReportOutOfBounds() {
            GpsPositionDTO pos = new GpsPositionDTO();
            pos.setDeviceId("d-1");
            pos.setLatitude(0.0);
            pos.setLongitude(0.0);
            assertEquals(GpsValidationResult.OUT_OF_BOUNDS, service.validateGpsPosition(pos));
        }

        @Test
        void validPositionPassesAllChecks() {
            GpsPositionDTO pos = new GpsPositionDTO();
            pos.setDeviceId("d-1");
            pos.setLatitude(37.9601);
            pos.setLongitude(58.3261);
            assertEquals(GpsValidationResult.VALID, service.validateGpsPosition(pos));
            assertTrue(service.isValidGpsPosition(pos));
        }
    }

    @Test
    void isWithinServiceAreaMirrorsCoordinatesCheck() {
        assertTrue(service.isWithinServiceArea(37.9601, 58.3261));
        assertFalse(service.isWithinServiceArea(0.0, 0.0));
    }
}
