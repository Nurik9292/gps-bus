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

    /** Distance after passing a stop where the bus accelerates back to cruising speed (meters). */
    private double stopAccelerationZoneMeters = 60.0;

    /** At the start of the acceleration zone (right after the stop), speed is this fraction
     *  of the base speed. Linearly ramps up to 1.0 at the end of the zone. */
    private double stopAccelerationMinFactor = 0.3;

    private long freshGpsWindowMs = 12_000;

    /** After freshGpsWindowMs and before this threshold: normal decay (decayFactor).
     *  After this threshold: aggressive decay (aggressiveDecayFactor).
     *  MUST be greater than freshGpsWindowMs, otherwise the normal-decay branch is unreachable. */
    private long aggressiveDecayAfterMs = 20_000;
  
    private double aggressiveDecayFactor = 0.90;

    /** After this many ms with no GPS, prediction stops advancing the position entirely.
     *  MUST be greater than aggressiveDecayAfterMs to give the aggressive-decay branch room. */
    private long stopAdvanceAfterMs = 30_000;

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
