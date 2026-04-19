package biz.ugur.busroutebackend.transport.domain.valueobject;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransportEnumsTest {

    @Nested
    class GpsValidationResults {

        @Test
        void validIsTheOnlyValidResult() {
            assertTrue(GpsValidationResult.VALID.isValid());
            for (GpsValidationResult r : GpsValidationResult.values()) {
                if (r != GpsValidationResult.VALID) {
                    assertFalse(r.isValid(), r.name());
                }
            }
        }

        @Test
        void onlyOutOfBoundsIsOutOfBounds() {
            assertTrue(GpsValidationResult.OUT_OF_BOUNDS.isOutOfBounds());
            for (GpsValidationResult r : GpsValidationResult.values()) {
                if (r != GpsValidationResult.OUT_OF_BOUNDS) {
                    assertFalse(r.isOutOfBounds(), r.name());
                }
            }
        }

        @Test
        void eachResultHasHumanReadableDescription() {
            for (GpsValidationResult r : GpsValidationResult.values()) {
                assertNotNull(r.getDescription());
                assertFalse(r.getDescription().isBlank());
            }
        }
    }

    @Nested
    class RouteSources {

        @Test
        void defaultConfidencesFollowDomainRules() {
            assertEquals(100, RouteSource.SHIFT_ASSIGNMENT.getDefaultConfidence());
            assertEquals(100, RouteSource.MANUAL.getDefaultConfidence());
            assertEquals(0, RouteSource.GPS_DETECTED.getDefaultConfidence());
            assertEquals(0, RouteSource.UNKNOWN.getDefaultConfidence());
        }

        @Test
        void shiftAssignmentHasHighestPriority() {
            assertTrue(RouteSource.SHIFT_ASSIGNMENT.hasHigherPriorityThan(RouteSource.GPS_DETECTED));
            assertTrue(RouteSource.SHIFT_ASSIGNMENT.hasHigherPriorityThan(RouteSource.MANUAL));
            assertTrue(RouteSource.SHIFT_ASSIGNMENT.hasHigherPriorityThan(RouteSource.UNKNOWN));
        }

        @Test
        void gpsDetectedBeatsManualAndUnknown() {
            assertTrue(RouteSource.GPS_DETECTED.hasHigherPriorityThan(RouteSource.MANUAL));
            assertTrue(RouteSource.GPS_DETECTED.hasHigherPriorityThan(RouteSource.UNKNOWN));
            assertFalse(RouteSource.GPS_DETECTED.hasHigherPriorityThan(RouteSource.SHIFT_ASSIGNMENT));
        }

        @Test
        void unknownHasLowestPriority() {
            for (RouteSource other : RouteSource.values()) {
                if (other != RouteSource.UNKNOWN) {
                    assertFalse(RouteSource.UNKNOWN.hasHigherPriorityThan(other), other.name());
                }
            }
        }

        @Test
        void isAutomaticForShiftAndGps() {
            assertTrue(RouteSource.SHIFT_ASSIGNMENT.isAutomatic());
            assertTrue(RouteSource.GPS_DETECTED.isAutomatic());
            assertFalse(RouteSource.MANUAL.isAutomatic());
            assertFalse(RouteSource.UNKNOWN.isAutomatic());
        }

        @Test
        void isReliableForShiftAndManual() {
            assertTrue(RouteSource.SHIFT_ASSIGNMENT.isReliable());
            assertTrue(RouteSource.MANUAL.isReliable());
            assertFalse(RouteSource.GPS_DETECTED.isReliable());
            assertFalse(RouteSource.UNKNOWN.isReliable());
        }
    }

    @Nested
    class GpsProviderTypes {

        @Test
        void defaultProviderIsChina() {
            assertEquals(GpsProviderType.CHINA, GpsProviderType.defaultProvider());
            assertTrue(GpsProviderType.CHINA.isDefault());
            assertFalse(GpsProviderType.TUGDK.isDefault());
        }

        @Test
        void fromCodeHandlesExactMatch() {
            assertEquals(GpsProviderType.TUGDK, GpsProviderType.fromCode("TUGDK"));
            assertEquals(GpsProviderType.CHINA, GpsProviderType.fromCode("CHINA"));
        }

        @Test
        void fromCodeIsCaseInsensitiveAndTrims() {
            assertEquals(GpsProviderType.TUGDK, GpsProviderType.fromCode("  tugdk  "));
            assertEquals(GpsProviderType.CHINA, GpsProviderType.fromCode("china"));
        }

        @Test
        void fromCodeReturnsDefaultForBlank() {
            assertEquals(GpsProviderType.CHINA, GpsProviderType.fromCode(null));
            assertEquals(GpsProviderType.CHINA, GpsProviderType.fromCode("   "));
        }

        @Test
        void fromCodeThrowsForUnknownCode() {
            assertThrows(IllegalArgumentException.class, () -> GpsProviderType.fromCode("yandex"));
        }

        @Test
        void fromCodeOrDefaultSwallowsUnknown() {
            assertEquals(GpsProviderType.CHINA, GpsProviderType.fromCodeOrDefault("yandex"));
            assertEquals(GpsProviderType.TUGDK, GpsProviderType.fromCodeOrDefault("tugdk"));
        }

        @Test
        void toStringReturnsCode() {
            assertEquals("TUGDK", GpsProviderType.TUGDK.toString());
            assertEquals("CHINA", GpsProviderType.CHINA.toString());
        }
    }
}
