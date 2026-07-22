package biz.ugur.busroutebackend.shared.infrastructure.email;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SmtpQuietHoursTest {

    private static SmtpEmailNotificationService service(int fromHour, int toHour) {
        MailProperties properties = new MailProperties();
        properties.setQuietFromHour(fromHour);
        properties.setQuietToHour(toHour);
        return new SmtpEmailNotificationService(properties,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    private static Instant ashgabat(String localTime) {
        return Instant.parse("2026-07-22T" + localTime + ":00Z")
                .minusSeconds(5 * 3600);
    }

    @Test
    void nightWindowCrossesMidnight() {
        SmtpEmailNotificationService smtp = service(23, 7);
        assertThat(smtp.inQuietHours(ashgabat("22:59"))).isFalse();
        assertThat(smtp.inQuietHours(ashgabat("23:00"))).isTrue();
        assertThat(smtp.inQuietHours(ashgabat("03:30"))).isTrue();
        assertThat(smtp.inQuietHours(ashgabat("06:59"))).isTrue();
        assertThat(smtp.inQuietHours(ashgabat("07:00"))).isFalse();
        assertThat(smtp.inQuietHours(ashgabat("12:00"))).isFalse();
    }

    @Test
    void equalBoundsDisableQuietHours() {
        SmtpEmailNotificationService smtp = service(0, 0);
        assertThat(smtp.inQuietHours(ashgabat("03:00"))).isFalse();
    }

    @Test
    void daytimeWindowWorksWithoutMidnightWrap() {
        SmtpEmailNotificationService smtp = service(13, 15);
        assertThat(smtp.inQuietHours(ashgabat("12:59"))).isFalse();
        assertThat(smtp.inQuietHours(ashgabat("14:00"))).isTrue();
        assertThat(smtp.inQuietHours(ashgabat("15:00"))).isFalse();
    }
}
