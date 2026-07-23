package biz.ugur.busroutebackend.transport.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "business.gps-outlier-detection")
public class GpsOutlierDetectionProperties {

    private boolean enabled = true;

    private double maxImpliedSpeedKmh = 150.0;

    private Duration minTimeDifference = Duration.ofSeconds(3);

    private Duration maxTimeDifference = Duration.ofSeconds(60);

    private double minDistanceMeters = 10.0;

    private int consecutiveOutliersForAlert = 2;

    private boolean rejectOutliers = false;

    private int historyPointsToCheck = 3;

    private double minSpeedForFrozenDetectionKmh = 10.0;

    private boolean rejectFrozenMotion = false;

    private Duration frozenWarnDedupInterval = Duration.ofMinutes(10);

    private Duration frozenChronicThreshold = Duration.ofMinutes(10);

    private Duration frozenEpisodeRetention = Duration.ofMinutes(30);
}
