package biz.ugur.busroutebackend.routing.domain.valueobjects;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RoutingValueObjectsTest {

    @Nested
    class TripPlanIds {

        @Test
        void generateProducesUuidLikeValue() {
            TripPlanId id = TripPlanId.generate();
            assertNotNull(id.getValue());
            assertEquals(36, id.getValue().length());
        }

        @Test
        void ofRejectsBlank() {
            assertThrows(IllegalArgumentException.class, () -> TripPlanId.of(""));
            assertThrows(IllegalArgumentException.class, () -> TripPlanId.of("   "));
            assertThrows(IllegalArgumentException.class, () -> TripPlanId.of(null));
        }

        @Test
        void ofTrimsValue() {
            assertEquals("abc", TripPlanId.of("  abc  ").getValue());
        }

        @Test
        void equalityBasedOnValue() {
            assertEquals(TripPlanId.of("x"), TripPlanId.of("x"));
            assertNotEquals(TripPlanId.of("x"), TripPlanId.of("y"));
        }
    }

    @Nested
    class TripSearchCriteriaPresets {

        @Test
        void defaultCriteriaEnablesBothPriorities() {
            TripSearchCriteria c = TripSearchCriteria.defaultCriteria();
            assertEquals(1200, c.getMaxWalkingDistanceMeters());
            assertEquals(2, c.getMaxTransfers());
            assertTrue(c.isPrioritizeSpeed());
            assertTrue(c.isPrioritizeFewerTransfers());
        }

        @Test
        void fastestRouteRelaxesTransfers() {
            TripSearchCriteria c = TripSearchCriteria.fastestRoute();
            assertEquals(3, c.getMaxTransfers());
            assertTrue(c.isPrioritizeSpeed());
            assertFalse(c.isPrioritizeFewerTransfers());
        }

        @Test
        void fewestTransfersTightensWalkingBudget() {
            TripSearchCriteria c = TripSearchCriteria.fewestTransfers();
            assertEquals(1, c.getMaxTransfers());
            assertEquals(600, c.getMaxWalkingDistanceMeters());
            assertFalse(c.isPrioritizeSpeed());
            assertTrue(c.isPrioritizeFewerTransfers());
        }

        @Test
        void toStringContainsKeyFields() {
            String s = TripSearchCriteria.defaultCriteria().toString();
            assertTrue(s.contains("walk≤1200"));
            assertTrue(s.contains("transfers≤2"));
        }
    }

    @Nested
    class TimePeriods {

        @Test
        void fromHourCoversAllDay() {
            assertEquals(TimePeriod.NIGHT, TimePeriod.fromHour(0));
            assertEquals(TimePeriod.NIGHT, TimePeriod.fromHour(6));
            assertEquals(TimePeriod.MORNING_RUSH, TimePeriod.fromHour(7));
            assertEquals(TimePeriod.MORNING_RUSH, TimePeriod.fromHour(9));
            assertEquals(TimePeriod.DAYTIME, TimePeriod.fromHour(12));
            assertEquals(TimePeriod.EVENING_RUSH, TimePeriod.fromHour(18));
            assertEquals(TimePeriod.EVENING, TimePeriod.fromHour(21));
            assertEquals(TimePeriod.NIGHT, TimePeriod.fromHour(23));
        }

        @Test
        void fromDateTimeUsesHour() {
            LocalDateTime eveningRush = LocalDateTime.of(2026, 5, 1, 18, 30);
            assertEquals(TimePeriod.EVENING_RUSH, TimePeriod.fromDateTime(eveningRush));
        }

        @Test
        void isWeekendDetectsSaturdayAndSunday() {
            assertTrue(TimePeriod.isWeekend(LocalDateTime.of(2026, 5, 2, 10, 0)));
            assertTrue(TimePeriod.isWeekend(LocalDateTime.of(2026, 5, 3, 10, 0)));
            assertFalse(TimePeriod.isWeekend(LocalDateTime.of(2026, 5, 4, 10, 0)));
        }

        @Test
        void rushHourFlagCoversMorningAndEveningOnly() {
            assertTrue(TimePeriod.MORNING_RUSH.isRushHour());
            assertTrue(TimePeriod.EVENING_RUSH.isRushHour());
            assertFalse(TimePeriod.DAYTIME.isRushHour());
            assertFalse(TimePeriod.NIGHT.isRushHour());
        }

        @Test
        void isNightOnlyForNight() {
            assertTrue(TimePeriod.NIGHT.isNight());
            assertFalse(TimePeriod.EVENING.isNight());
        }

        @Test
        void weekendReducesTrafficMultiplier() {
            double weekday = TimePeriod.MORNING_RUSH.getTrafficMultiplier(false);
            double weekend = TimePeriod.MORNING_RUSH.getTrafficMultiplier(true);
            assertTrue(weekend < weekday);
            assertEquals(weekday * 0.8, weekend, 1e-9);
        }

        @Test
        void weekendIncreasesWaitingCappedAt30() {
            int weekday = TimePeriod.EVENING.getBaseWaitingMinutes(false);
            int weekend = TimePeriod.EVENING.getBaseWaitingMinutes(true);
            assertEquals(weekday + 5, weekend);

            int nightWeekend = TimePeriod.NIGHT.getBaseWaitingMinutes(true);
            assertEquals(30, nightWeekend);
        }
    }
}
