package biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.gps-alerts")
public class GpsAlertProperties {

    private boolean enabled = false;
    private String recipients = "";

    private HttpError httpError = new HttpError();
    private Empty empty = new Empty();
    private Drop drop = new Drop();
    private Stale stale = new Stale();
    private Recovery recovery = new Recovery();

    public List<String> recipientList() {
        if (recipients == null || recipients.isBlank()) {
            return List.of();
        }
        return Arrays.stream(recipients.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Getter
    @Setter
    public static class HttpError {
        private int consecutiveFailures = 3;
    }

    @Getter
    @Setter
    public static class Empty {
        private int consecutiveEmpty = 3;
    }

    @Getter
    @Setter
    public static class Drop {
        private int thresholdPercent = 50;
        private int baselineWindowHours = 24;
        private int minBaseline = 10;
    }

    @Getter
    @Setter
    public static class Stale {
        private int maxFixAgeMinutes = 5;
        private int degradedPercent = 95;
    }

    @Getter
    @Setter
    public static class Recovery {
        private int clearFetches = 3;
    }
}
