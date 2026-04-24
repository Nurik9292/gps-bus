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

    private int maxAgeMs = 90_000;

    private double minSpeedKmh = 3.0;

    private double decayFactor = 0.98;

    private double conservativeSpeedFactor = 0.92;

    private boolean snapToRoute = true;

    private double stopDecelerationZoneMeters = 80.0;

    private double stopDecelerationMinFactor = 0.15;

    private double stopAccelerationZoneMeters = 60.0;

    private double stopAccelerationMinFactor = 0.3;

    private long freshGpsWindowMs = 12_000;

    private long aggressiveDecayAfterMs = 20_000;
  
    private double aggressiveDecayFactor = 0.90;

    private long stopAdvanceAfterMs = 90_000;

    private double teleportThresholdMeters = 300.0;

    private double directionFlipMaxDistanceMeters = 200.0;

    private double terminalFractionTolerance = 0.05;

    private long stoppedBroadcastIntervalMs = 10_000;

    private double dwellTimeSeconds = 15.0;

    private double dwellActivationDistanceMeters = 100.0;

    private double dwellSpeedThresholdKmh = 15.0;

    private long coldStartDurationSec = 15;

    private double directionFlipThresholdDeg = 90.0;

    private int oppositeSnapThreshold = 3;

    private double terminalFlipMaxPhysicalJumpMeters = 500.0;
}
