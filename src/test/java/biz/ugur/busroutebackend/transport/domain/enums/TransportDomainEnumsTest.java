package biz.ugur.busroutebackend.transport.domain.enums;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class TransportDomainEnumsTest {

    @Nested
    class RouteDirectionTests {

        @Test
        void valuesMapToIntegers() {
            assertEquals(0, RouteDirection.FORWARD.getValue());
            assertEquals(1, RouteDirection.BACKWARD.getValue());
        }

        @Test
        void fromValueDecodesCorrectly() {
            assertEquals(RouteDirection.FORWARD, RouteDirection.fromValue(0));
            assertEquals(RouteDirection.BACKWARD, RouteDirection.fromValue(1));
        }

        @Test
        void fromValueRejectsUnknown() {
            assertThrows(IllegalArgumentException.class, () -> RouteDirection.fromValue(2));
            assertThrows(IllegalArgumentException.class, () -> RouteDirection.fromValue(-1));
        }

        @Test
        void oppositeFlipsDirection() {
            assertEquals(RouteDirection.BACKWARD, RouteDirection.FORWARD.opposite());
            assertEquals(RouteDirection.FORWARD, RouteDirection.BACKWARD.opposite());
        }
    }

    @Nested
    class ShiftTypeTests {

        @Test
        void firstShiftBoundaries() {
            assertEquals(LocalTime.of(5, 0), ShiftType.FIRST.getStartTime());
            assertEquals(LocalTime.of(14, 0), ShiftType.FIRST.getEndTime());
        }

        @Test
        void secondShiftBoundaries() {
            assertEquals(LocalTime.of(14, 0), ShiftType.SECOND.getStartTime());
            assertEquals(LocalTime.of(23, 0), ShiftType.SECOND.getEndTime());
        }

        @Test
        void fullDayShiftBoundaries() {
            assertEquals(LocalTime.of(5, 0), ShiftType.FULL_DAY.getStartTime());
            assertEquals(LocalTime.of(23, 0), ShiftType.FULL_DAY.getEndTime());
        }

        @Test
        void isActiveAtFirstShiftHours() {
            assertTrue(ShiftType.FIRST.isActiveAt(LocalTime.of(8, 0)));
            assertTrue(ShiftType.FIRST.isActiveAt(LocalTime.of(5, 0)));
            assertFalse(ShiftType.FIRST.isActiveAt(LocalTime.of(14, 0)));
            assertFalse(ShiftType.FIRST.isActiveAt(LocalTime.of(4, 59)));
        }

        @Test
        void isActiveAtSecondShiftHours() {
            assertTrue(ShiftType.SECOND.isActiveAt(LocalTime.of(18, 0)));
            assertFalse(ShiftType.SECOND.isActiveAt(LocalTime.of(13, 59)));
            assertFalse(ShiftType.SECOND.isActiveAt(LocalTime.of(23, 0)));
        }

        @Test
        void fullDayIsActiveAcrossBothShifts() {
            assertTrue(ShiftType.FULL_DAY.isActiveAt(LocalTime.of(7, 0)));
            assertTrue(ShiftType.FULL_DAY.isActiveAt(LocalTime.of(18, 0)));
            assertFalse(ShiftType.FULL_DAY.isActiveAt(LocalTime.of(1, 0)));
        }

        @Test
        void fromStringAcceptsVariousLabels() {
            assertEquals(ShiftType.FIRST, ShiftType.fromString("FIRST"));
            assertEquals(ShiftType.FIRST, ShiftType.fromString("1"));
            assertEquals(ShiftType.FIRST, ShiftType.fromString("first"));
            assertEquals(ShiftType.SECOND, ShiftType.fromString("SECOND"));
            assertEquals(ShiftType.SECOND, ShiftType.fromString("2"));
            assertEquals(ShiftType.FULL_DAY, ShiftType.fromString("FULL_DAY"));
            assertEquals(ShiftType.FULL_DAY, ShiftType.fromString("fullday"));
            assertEquals(ShiftType.FULL_DAY, ShiftType.fromString("full"));
        }

        @Test
        void fromStringTrimsInput() {
            assertEquals(ShiftType.FIRST, ShiftType.fromString("  first  "));
        }

        @Test
        void fromStringRejectsBlank() {
            assertThrows(IllegalArgumentException.class, () -> ShiftType.fromString(null));
            assertThrows(IllegalArgumentException.class, () -> ShiftType.fromString("   "));
        }

        @Test
        void fromStringRejectsUnknown() {
            assertThrows(IllegalArgumentException.class, () -> ShiftType.fromString("night"));
        }

        @Test
        void getCurrentShiftNeverNull() {
            assertNotNull(ShiftType.getCurrentShift());
        }

        @Test
        void isActiveIsAShortcutForActiveAtNow() {
            for (ShiftType s : ShiftType.values()) {
                boolean direct = s.isActiveAt(java.time.ZonedDateTime.now(ShiftType.ASHGABAT_ZONE).toLocalTime());
                assertEquals(direct, s.isActive(), s.name());
            }
        }
    }
}
