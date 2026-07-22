package biz.ugur.busroutebackend.shared.infrastructure.email;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class AlertQuietHours {

    private static final ZoneId ASHGABAT = ZoneId.of("Asia/Ashgabat");

    private final MailProperties properties;
    private final Clock clock;

    public AlertQuietHours(MailProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean active() {
        return active(clock.instant());
    }

    public boolean active(Instant now) {
        int fromHour = properties.getQuietFromHour();
        int toHour = properties.getQuietToHour();
        if (fromHour == toHour) {
            return false;
        }
        int hour = LocalDateTime.ofInstant(now, ASHGABAT).getHour();
        if (fromHour < toHour) {
            return hour >= fromHour && hour < toHour;
        }
        return hour >= fromHour || hour < toHour;
    }
}
