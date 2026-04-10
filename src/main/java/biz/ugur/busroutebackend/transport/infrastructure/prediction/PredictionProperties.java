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

    /** Normal speed decay factor per tick (applied every prediction tick). */
    private double decayFactor = 0.98;

    /** Conservative speed factor: prediction advances at this fraction of reported GPS speed.
     *  Values < 1.0 guarantee "always lag, never lead" — the marker moves slower than reality,
     *  so fresh GPS always arrives AHEAD of prediction → snap is always forward.
     *  At 0.92: after 6s GPS interval at 60km/h, gap ≈ 8m (imperceptible snap). */
    private double conservativeSpeedFactor = 0.92;

    private boolean snapToRoute = true;

    private double stopDecelerationZoneMeters = 80.0;

    private double stopDecelerationMinFactor = 0.15;

    /** While GPS data is this fresh (ms), prediction uses constant speed (no decay at all).
     *  This prevents premature slowdown during normal 6-second GPS polling intervals.
     *  Set to cover ~2 GPS cycles so prediction maintains speed even if one GPS is skipped. */
    private long freshGpsWindowMs = 12_000;

    /** When GPS has not been received for longer than this (ms), switch to aggressive decay.
     *  Speed decays faster to prevent prediction from drifting too far without confirmation. */
    private long aggressiveDecayAfterMs = 10_000;

    /** Decay factor per tick when in aggressive decay mode (no GPS for > aggressiveDecayAfterMs). */
    private double aggressiveDecayFactor = 0.90;

    /** After this many ms without GPS, prediction stops advancing entirely.
     *  The marker freezes at the last predicted position. */
    private long stopAdvanceAfterMs = 20_000;

    /** Snap distance threshold: if fresh GPS snap position is farther than this from current
     *  predicted position, treat it as a teleport (instant jump, logged as discontinuity).
     *  Below this threshold — instant snap (visually acceptable). */
    private double teleportThresholdMeters = 300.0;

    /** Direction flip: maximum physical distance a direction change is allowed to cause.
     *  Prevents end-to-end route teleports. */
    private double directionFlipMaxDistanceMeters = 200.0;

    /** A vehicle is "at a terminal" when fraction is within this of 0 or 1.
     *  Direction flips near terminals are always allowed. */
    private double terminalFractionTolerance = 0.05;

    /** How often to broadcast stopped vehicles (ms). Stopped vehicles are included in
     *  prediction broadcasts at this interval to keep Flutter markers alive. */
    private long stoppedBroadcastIntervalMs = 10_000;

    /** Average dwell time at a bus stop in seconds.
     *  When prediction reaches a stop fraction, it "pauses" for this many seconds
     *  before continuing. Prevents prediction from overshooting past stops. */
    private double dwellTimeSeconds = 15.0;

    /** Distance in meters from the stop at which dwell pause activates.
     *  If prediction is within this distance of a stop and raw GPS speed is < dwellSpeedThresholdKmh,
     *  prediction assumes the bus is dwelling and freezes until GPS confirms departure. */
    private double dwellActivationDistanceMeters = 30.0;

    /** Speed below which the bus is considered potentially dwelling at a stop (km/h). */
    private double dwellSpeedThresholdKmh = 5.0;
}
