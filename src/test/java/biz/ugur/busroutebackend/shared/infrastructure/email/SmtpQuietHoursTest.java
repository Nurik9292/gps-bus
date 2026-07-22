package biz.ugur.busroutebackend.shared.infrastructure.email;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SmtpQuietHoursTest {

    private static AlertQuietHours quietHours(int fromHour, int toHour) {
        MailProperties properties = new MailProperties();
        properties.setQuietFromHour(fromHour);
        properties.setQuietToHour(toHour);
        return new AlertQuietHours(properties, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    private static Instant ashgabat(String localTime) {
        return Instant.parse("2026-07-22T" + localTime + ":00Z")
                .minusSeconds(5 * 3600);
    }

    @Test
    void nightWindowCrossesMidnight() {
        AlertQuietHours quiet = quietHours(23, 7);
        assertThat(quiet.active(ashgabat("22:59"))).isFalse();
        assertThat(quiet.active(ashgabat("23:00"))).isTrue();
        assertThat(quiet.active(ashgabat("03:30"))).isTrue();
        assertThat(quiet.active(ashgabat("06:59"))).isTrue();
        assertThat(quiet.active(ashgabat("07:00"))).isFalse();
        assertThat(quiet.active(ashgabat("12:00"))).isFalse();
    }

    @Test
    void equalBoundsDisableQuietHours() {
        assertThat(quietHours(0, 0).active(ashgabat("03:00"))).isFalse();
    }

    @Test
    void daytimeWindowWorksWithoutMidnightWrap() {
        AlertQuietHours quiet = quietHours(13, 15);
        assertThat(quiet.active(ashgabat("12:59"))).isFalse();
        assertThat(quiet.active(ashgabat("14:00"))).isTrue();
        assertThat(quiet.active(ashgabat("15:00"))).isFalse();
    }
}
