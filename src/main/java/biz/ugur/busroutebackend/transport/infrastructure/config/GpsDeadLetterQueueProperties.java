package biz.ugur.busroutebackend.transport.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "business.gps-dlq")
public class GpsDeadLetterQueueProperties {

    private boolean enabled = true;

    private int maxRetries = 3;

    private Duration retryDelay = Duration.ofMinutes(1);

    private double retryMultiplier = 2.0;

    private Duration maxRetryDelay = Duration.ofMinutes(30);

    private Duration ttl = Duration.ofHours(24);

    private int retryBatchSize = 50;

    private boolean logFailures = true;

    public Duration calculateNextRetryDelay(int currentRetryCount) {
        long delayMillis = (long) (retryDelay.toMillis() * Math.pow(retryMultiplier, currentRetryCount));
        return Duration.ofMillis(Math.min(delayMillis, maxRetryDelay.toMillis()));
    }
}
