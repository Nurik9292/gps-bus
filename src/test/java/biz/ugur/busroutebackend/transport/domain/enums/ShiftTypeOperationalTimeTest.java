package biz.ugur.busroutebackend.transport.domain.enums;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ShiftTypeOperationalTimeTest {

    @Test
    void eveningUtcIsSecondShiftInAshgabat() {
        assertThat(ShiftType.operationalShiftAt(Instant.parse("2026-07-22T12:57:00Z")))
                .contains(ShiftType.SECOND);
    }

    @Test
    void morningUtcIsFirstShiftInAshgabat() {
        assertThat(ShiftType.operationalShiftAt(Instant.parse("2026-07-22T04:00:00Z")))
                .contains(ShiftType.FIRST);
    }

    @Test
    void ashgabatNightHasNoOperationalShift() {
        assertThat(ShiftType.operationalShiftAt(Instant.parse("2026-07-22T19:30:00Z")))
                .isEmpty();
    }

    @Test
    void operationalDateRollsOverAtAshgabatMidnight() {
        assertThat(ShiftType.operationalDateAt(Instant.parse("2026-07-22T20:30:00Z")))
                .isEqualTo(LocalDate.of(2026, 7, 23));
        assertThat(ShiftType.operationalDateAt(Instant.parse("2026-07-22T12:00:00Z")))
                .isEqualTo(LocalDate.of(2026, 7, 22));
    }

    @Test
    void secondShiftStartsAtNineUtc() {
        assertThat(ShiftType.SECOND.startInstantOn(LocalDate.of(2026, 7, 22)))
                .isEqualTo(Instant.parse("2026-07-22T09:00:00Z"));
    }
}
