package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Holds the prediction state for a single vehicle.
 * Updated on every real GPS fix and on every prediction cycle.
 */
@Data
@Builder(toBuilder = true)
public class VehiclePredictionState {

    private String vehicleId;
    private String licensePlate;
    private String routeNumber;

    // ---- Last known GPS data ----
    private double gpsLatitude;
    private double gpsLongitude;
    /** Decayed speed carried forward between prediction cycles (km/h) */
    private double speedKmh;
    /** Heading in degrees (0 = North, 90 = East) */
    private double course;
    private boolean inMotion;
    /** GPS fix timestamp from the device (used for deduplication and outlier time-delta). */
    private Instant lastGpsUpdate;
    /**
     * Server-side time when we last received a GPS update for this vehicle.
     * Used for the prediction age filter — GPS fix timestamps can lag server time
     * by 5–30 s (batching + network), so comparing fix time to maxAgeMs (10 s default)
     * would immediately exclude every vehicle. Receipt time is always "now".
     */
    private Instant lastReceivedAt;

    // ---- Predicted position (updated each scheduler tick) ----
    private double predictedLatitude;
    private double predictedLongitude;

    // ---- Phase 2: route-aware prediction ----

    /**
     * Cached route geometry points as [lat, lon] pairs.
     * {@code null} when no route is assigned or geometry is unavailable.
     */
    private List<double[]> routeCoordinates;

    /** Total route length in metres. 0 when routeCoordinates is null. */
    private double totalRouteDistanceMeters;

    /**
     * Current position along the route as a fraction [0.0, 1.0].
     * -1 when not yet snapped to a route.
     */
    @Builder.Default
    private double fractionOnRoute = -1;

    /** 0 = forward (fraction grows), 1 = backward (fraction decreases). */
    @Builder.Default
    private int direction = 0;

    // ---- Phase 3: smooth GPS correction (dead-reckoning mode) ----

    /**
     * Target latitude toward which the predicted position is smoothly corrected.
     * Zero when no correction is active.
     */
    @Builder.Default
    private double correctionTargetLat = 0;

    /** Target longitude for smooth correction. Zero when no correction is active. */
    @Builder.Default
    private double correctionTargetLon = 0;

    /**
     * Number of prediction cycles remaining until the smooth correction finishes.
     * Zero means no correction is active.
     */
    @Builder.Default
    private int correctionCyclesLeft = 0;
}
