package biz.ugur.busroutebackend.transport.infrastructure.monitoring.offroute;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.off-route-alerts")
public class OffRouteAlertProperties {

    private boolean enabled = false;
    private String recipients = "";
    private int endOfShiftBufferMinutes = 15;
    private int minOnRouteSeconds = 60;

    public List<String> recipientList() {
        if (recipients == null || recipients.isBlank()) {
            return List.of();
        }
        return Arrays.stream(recipients.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
