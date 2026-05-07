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

    private double maxBusSpeedMs = 22.0;

    private double outlierTolerance = 1.2;

    private double maxTeleportDistanceMeters = 3_000.0;

    private double hardOutlierMeters = 2_000.0;

    private long hardOutlierWindowMs = 10_000;

    private long softOutlierWindowMs = 60_000;

    private double maxSnapDistanceMeters = 150.0;

    private long dirChangeCooldownMs = 5_000;

    private double offRouteDistanceThresholdMeters = 200.0;

    private int offRouteConfirmations = 5;

    private double hardOffRouteDistanceMeters = 500.0;

    private double stationaryGpsThresholdMeters = 100.0;

    private double stalePredictedFromGpsMeters = 500.0;

    private int teleportCommitConfirmations = 8;

    private int teleportCommitConfirmationsTrajectory = 2;

    private double teleportCommitRadiusMeters = 150.0;

    private double teleportTrajectoryStepMeters = 500.0;

    private double teleportTrajectoryFracDeltaMax = 0.1;

    private long teleportCommitWindowMs = 120_000;

    private long teleportFastConfirmAfterMs = 30_000;

    private double positionJumpInternalThresholdMeters = 500.0;

    private int forceAcceptCount = 5;

    private double forceAcceptClusterRadiusMeters = 150.0;

    private long forceAcceptWindowMs = 120_000;

    private double stopDecelerationTriggerMeters = 300.0;

    private double dwellMinSeconds = 3.0;

    private double dwellMaxSeconds = 600.0;

    private int dwellMinSamples = 3;

    private double realStopLongTermSpeedKmh = 2.0;

    private double trafficCrawlMinSpeedKmh = 2.0;

    private double trafficCrawlMaxSpeedKmh = 12.0;

    private double catchUpErrorThreshold = 0.002;

    private double catchUpGain = 0.30;

    private double catchUpMaxPerTick = 0.005;

    private double stationarySpeedThresholdKmh = 5.0;

    private double windowedSnapFractionWindow = 0.20;

    private double fracFlipPlausibleJumpThreshold = 0.25;
}
