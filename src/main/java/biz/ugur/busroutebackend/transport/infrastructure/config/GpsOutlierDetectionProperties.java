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

    /**
     * Minimum reported speed (km/h) above which frozen coordinates are considered an anomaly.
     * If reported speed > this threshold but coordinates haven't changed, it's a stale GPS fix.
     */
    private double minSpeedForFrozenDetectionKmh = 10.0;

    /**
     * Whether to reject (drop) updates with frozen coordinates + motion.
     * Default false — log anomaly but still update speed/motion state.
     */
    private boolean rejectFrozenMotion = false;
}
