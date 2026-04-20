package biz.ugur.busroutebackend.transport.domain.specification;

import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopCode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BusStopSpecificationsTest {

    private BusStop majorStop() {
        return BusStop.create("Berkararlyk", "Berkararlyk", "Berkararlyk",
                StopCode.of("ASH001"),
                new BigDecimal("37.9601"), new BigDecimal("58.3261"),
                true, "city-001", "admin");
    }

    private BusStop regularInactive() {
        return majorStop().unmarkAsMajorStop().deactivate();
    }

    @Nested
    class ActiveAndMajor {

        @Test
        void isActiveMatchesActive() {
            assertTrue(BusStopSpecifications.isActive().isSatisfiedBy(majorStop()));
            assertFalse(BusStopSpecifications.isActive().isSatisfiedBy(regularInactive()));
        }

        @Test
        void isInactiveMatchesDeactivated() {
            assertTrue(BusStopSpecifications.isInactive().isSatisfiedBy(regularInactive()));
        }

        @Test
        void isMajorStopMatchesMajor() {
            assertTrue(BusStopSpecifications.isMajorStop().isSatisfiedBy(majorStop()));
            assertFalse(BusStopSpecifications.isMajorStop().isSatisfiedBy(
                    majorStop().unmarkAsMajorStop()));
        }

        @Test
        void isRegularStopMatchesMinor() {
            assertTrue(BusStopSpecifications.isRegularStop().isSatisfiedBy(
                    majorStop().unmarkAsMajorStop()));
            assertFalse(BusStopSpecifications.isRegularStop().isSatisfiedBy(majorStop()));
        }
    }

    @Nested
    class LocationAndCity {

        @Test
        void hasLocationTrueForStopWithCoordinates() {
            assertTrue(BusStopSpecifications.hasLocation().isSatisfiedBy(majorStop()));
        }

        @Test
        void servesCityMatchesCityId() {
            assertTrue(BusStopSpecifications.servesCity("city-001").isSatisfiedBy(majorStop()));
            assertFalse(BusStopSpecifications.servesCity("city-002").isSatisfiedBy(majorStop()));
            assertFalse(BusStopSpecifications.servesCity(null).isSatisfiedBy(majorStop()));
        }

        @Test
        void withinBoundingBoxTrueForInsidePoint() {
            assertTrue(BusStopSpecifications.withinBoundingBox(
                    37.9, 58.3, 38.0, 58.4).isSatisfiedBy(majorStop()));
        }

        @Test
        void withinBoundingBoxFalseForOutsidePoint() {
            assertFalse(BusStopSpecifications.withinBoundingBox(
                    40.0, 60.0, 41.0, 61.0).isSatisfiedBy(majorStop()));
        }
    }

    @Nested
    class TextAndTranslations {

        @Test
        void nameContainsCaseInsensitive() {
            assertTrue(BusStopSpecifications.nameContains("berkar").isSatisfiedBy(majorStop()));
            assertFalse(BusStopSpecifications.nameContains("xyz").isSatisfiedBy(majorStop()));
        }

        @Test
        void hasEnglishTranslationTrueForNonBlankEnglish() {
            assertTrue(BusStopSpecifications.hasEnglishTranslation().isSatisfiedBy(majorStop()));
            BusStop noEn = BusStop.create("Stop", "", "tm",
                    StopCode.of("ASH002"),
                    new BigDecimal("37.96"), new BigDecimal("58.32"),
                    false, "city-001", "admin");
            assertFalse(BusStopSpecifications.hasEnglishTranslation().isSatisfiedBy(noEn));
        }

        @Test
        void hasTurkmenTranslationTrueForNonBlank() {
            assertTrue(BusStopSpecifications.hasTurkmenTranslation().isSatisfiedBy(majorStop()));
        }

        @Test
        void hasCompleteTranslationsRequiresAllLanguages() {
            assertTrue(BusStopSpecifications.hasCompleteTranslations().isSatisfiedBy(majorStop()));
        }
    }

    @Nested
    class Identity {

        @Test
        void hasIdMatchesGeneratedValue() {
            BusStop stop = majorStop();
            assertTrue(BusStopSpecifications.hasId(stop.getId().getValue()).isSatisfiedBy(stop));
            assertFalse(BusStopSpecifications.hasId("nope").isSatisfiedBy(stop));
        }

        @Test
        void hasStopCodeMatchesCodeValue() {
            assertTrue(BusStopSpecifications.hasStopCode("ASH001").isSatisfiedBy(majorStop()));
            assertFalse(BusStopSpecifications.hasStopCode("ASH999").isSatisfiedBy(majorStop()));
        }
    }

    @Nested
    class RouteAssociations {

        @Test
        void servesMultipleRoutesUsesCountDerivedFromFlag() {
            assertTrue(BusStopSpecifications.servesMultipleRoutes(2).isSatisfiedBy(majorStop()));
            BusStop minor = majorStop().unmarkAsMajorStop();
            assertFalse(BusStopSpecifications.servesMultipleRoutes(3).isSatisfiedBy(minor));
        }
    }

    @Nested
    class Composite {

        @Test
        void isReadyForServiceCombinesActiveAndLocation() {
            assertTrue(BusStopSpecifications.isReadyForService().isSatisfiedBy(majorStop()));
            assertFalse(BusStopSpecifications.isReadyForService().isSatisfiedBy(regularInactive()));
        }

        @Test
        void isMajorTransferPointRequiresActiveMajorAndRoutes() {
            assertTrue(BusStopSpecifications.isMajorTransferPoint().isSatisfiedBy(majorStop()));
            BusStop deactivatedMajor = majorStop().deactivate();
            assertFalse(BusStopSpecifications.isMajorTransferPoint().isSatisfiedBy(deactivatedMajor));
        }

        @Test
        void isFullyConfiguredNeedsActiveLocationAndTranslations() {
            assertTrue(BusStopSpecifications.isFullyConfigured().isSatisfiedBy(majorStop()));
        }

        @Test
        void requiresAttentionForActiveMissingDataPlaces() {
            BusStop noEn = BusStop.create("Stop", "", "tm",
                    StopCode.of("ASH002"),
                    new BigDecimal("37.96"), new BigDecimal("58.32"),
                    false, "city-001", "admin");
            assertTrue(BusStopSpecifications.requiresAttention().isSatisfiedBy(noEn));
            assertFalse(BusStopSpecifications.requiresAttention().isSatisfiedBy(majorStop()));
        }

        @Test
        void isImportantRequiresMajorWithThreePlusRoutes() {
            assertTrue(BusStopSpecifications.isImportant().isSatisfiedBy(majorStop()));
            BusStop minor = majorStop().unmarkAsMajorStop();
            assertFalse(BusStopSpecifications.isImportant().isSatisfiedBy(minor));
        }
    }
}
