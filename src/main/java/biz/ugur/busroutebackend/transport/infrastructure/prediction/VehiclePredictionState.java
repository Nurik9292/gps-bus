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
    private Instant lastGpsUpdate;

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
}
