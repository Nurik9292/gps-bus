package biz.ugur.busroutebackend.shared.infrastructure.external.gps.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;


@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "external.api.gps-common")
public class GpsProviderProperties {

    private RetryConfig retry = new RetryConfig();
    private TimeoutConfig timeout = new TimeoutConfig();

    @Getter
    @Setter
    public static class RetryConfig {
        private int maxAttempts = 3;
        private Duration initialInterval = Duration.ofSeconds(1);
        private Duration maxInterval = Duration.ofSeconds(5);
        private double multiplier = 2.0;
    }

    @Getter
    @Setter
    public static class TimeoutConfig {
        private Duration request = Duration.ofSeconds(30);
        private Duration healthCheck = Duration.ofSeconds(10);
    }
}
