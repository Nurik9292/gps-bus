package biz.ugur.busroutebackend.advertising.domain.enums;

import biz.ugur.busroutebackend.advertising.domain.exceptions.AdvertisingValidationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class AdvertisingEnumsTest {

    @Nested
    class PlacementTypeTests {

        @Test
        void fromAcceptsExactMatch() {
            assertEquals(PlacementType.BANNER, PlacementType.from("BANNER"));
            assertEquals(PlacementType.PUSH, PlacementType.from("PUSH"));
        }

        @Test
        void fromIsCaseInsensitiveAndTrims() {
            assertEquals(PlacementType.BANNER, PlacementType.from("  banner  "));
            assertEquals(PlacementType.POPUP, PlacementType.from("PopUp"));
        }

        @Test
        void fromRejectsNullOrBlank() {
            assertThrows(AdvertisingValidationException.class, () -> PlacementType.from(null));
            assertThrows(AdvertisingValidationException.class, () -> PlacementType.from("   "));
        }

        @Test
        void fromRejectsUnknown() {
            assertThrows(AdvertisingValidationException.class, () -> PlacementType.from("unknown"));
        }
    }

    @Nested
    class TariffPeriodTests {

        @Test
        void durationsMatchPeriod() {
            assertEquals(Duration.ofDays(1), TariffPeriod.DAY.getDuration());
            assertEquals(Duration.ofDays(7), TariffPeriod.WEEK.getDuration());
            assertEquals(Duration.ofDays(30), TariffPeriod.MONTH.getDuration());
            assertEquals(Duration.ofDays(90), TariffPeriod.QUARTER.getDuration());
            assertEquals(Duration.ofDays(365), TariffPeriod.YEAR.getDuration());
        }

        @Test
        void fromAcceptsKnownCodes() {
            assertEquals(TariffPeriod.WEEK, TariffPeriod.from("week"));
            assertEquals(TariffPeriod.MONTH, TariffPeriod.from("  MONTH  "));
        }

        @Test
        void fromRejectsNullOrBlank() {
            assertThrows(AdvertisingValidationException.class, () -> TariffPeriod.from(null));
            assertThrows(AdvertisingValidationException.class, () -> TariffPeriod.from(""));
        }

        @Test
        void fromRejectsUnknown() {
            assertThrows(AdvertisingValidationException.class, () -> TariffPeriod.from("decade"));
        }
    }
}
