package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ugur.prediction")
public class PredictionProperties {

    private boolean enabled = true;

    private int intervalMs = 1000;

    private int maxAgeMs = 10000;

    private double minSpeedKmh = 3.0;

    private double decayFactor = 0.98;

    private double conservativeSpeedFactor = 0.92;

    private boolean snapToRoute = true;

    private double stopDecelerationZoneMeters = 80.0;

    private double stopDecelerationMinFactor = 0.15;

    private long freshGpsWindowMs = 12_000;

    private long aggressiveDecayAfterMs = 10_000;
  
    private double aggressiveDecayFactor = 0.90;

    private long stopAdvanceAfterMs = 20_000;

    private double teleportThresholdMeters = 300.0;

    private double directionFlipMaxDistanceMeters = 200.0;

    private double terminalFractionTolerance = 0.05;

    private long stoppedBroadcastIntervalMs = 10_000;

    private double dwellTimeSeconds = 15.0;

    /** Distance to next stop at which dwell detection activates (meters).
     *  Must be large enough to catch buses decelerating (prediction lags GPS by ~50-100m). */
    private double dwellActivationDistanceMeters = 100.0;

    /** Speed below which dwell mode activates (km/h).
     *  GPS often reports 8-15 km/h during deceleration at stops.
     *  Must be high enough to catch the bus BEFORE it drops to 0 and exits moving state. */
    private double dwellSpeedThresholdKmh = 15.0;
}
