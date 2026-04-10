package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;


@Data
@Builder(toBuilder = true)
public class VehiclePredictionState {

    private String vehicleId;
    private String licensePlate;
    private String routeNumber;

    private double gpsLatitude;
    private double gpsLongitude;
    private double speedKmh;
    /** Raw GPS speed as received from the provider — not affected by conservative factor or decay.
     *  Used for accurate ETA calculation (prediction speed decays between GPS updates). */
    private double rawGpsSpeedKmh;
    /** Smoothed speed: rolling average of last N raw GPS speeds.
     *  Eliminates speed spikes (0→50→30→0→45) that cause jerky prediction advance.
     *  Used by advancePositionOnly instead of raw instantaneous speed. */
    private double smoothedSpeedKmh;
    /** Circular buffer of recent raw GPS speeds for smoothing. */
    private double[] recentSpeeds;
    private double course;
    private boolean inMotion;
    private Instant lastGpsUpdate;
    private Instant lastReceivedAt;

    private double predictedLatitude;
    private double predictedLongitude;


    private List<double[]> routeCoordinates;

    private double totalRouteDistanceMeters;

    @Builder.Default
    private double fractionOnRoute = -1;

    @Builder.Default
    private double lastGpsFraction = -1;

    /** Последняя фракция GPS которую SNAP_IMPLAUSIBLE отверг. */
    @Builder.Default
    private double lastRejectedGpsFraction = -1;

    /** Сколько раз подряд SNAP_IMPLAUSIBLE отверг GPS вблизи lastRejectedGpsFraction. */
    @Builder.Default
    private int consecutiveImplausibleCount = 0;

    @Builder.Default
    private int direction = 0;

    /** Last time this vehicle was broadcast via WebSocket (for stopped vehicle throttling). */
    private Instant lastBroadcastAt;

    /** When the vehicle entered dwell mode at a stop (null = not dwelling). */
    private Instant dwellStartedAt;

    /** Fraction of the stop where the vehicle is currently dwelling (-1 = not dwelling). */
    @Builder.Default
    private double dwellStopFraction = -1;

}
