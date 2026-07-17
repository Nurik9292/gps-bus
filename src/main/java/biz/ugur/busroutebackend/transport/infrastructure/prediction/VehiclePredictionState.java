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
    private String routeId;

    private double gpsLatitude;
    private double gpsLongitude;
    private double speedKmh;
    private double rawGpsSpeedKmh;
    private double smoothedSpeedKmh;
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

    @Builder.Default
    private double lastRejectedGpsFraction = -1;

    @Builder.Default
    private int consecutiveImplausibleCount = 0;

    @Builder.Default
    private int direction = 0;

    @Builder.Default
    private boolean directionConfirmed = false;

    private Instant lastBroadcastAt;

    private Instant dwellStartedAt;

    @Builder.Default
    private double dwellStopFraction = -1;

    private String dwellStopId;

    private Instant coldStartUntilAt;

    @Builder.Default
    private boolean inGarage = false;

    @Builder.Default
    private int consecutiveInconsistentAdvanceCount = 0;

    @Builder.Default
    private int consecutiveOffRouteCount = 0;

    @Builder.Default
    private boolean offRoute = false;

    private Instant firstOnRouteAtCurrentShift;

    private Instant lastOnRouteAt;

    @Builder.Default
    private double lastRawToSnapDistanceMeters = Double.NaN;

    @Builder.Default
    private double longTermAvgSpeedKmh = -1;

    @Builder.Default
    private double kalmanSpeedKmh = -1;

    @Builder.Default
    private double kalmanSpeedVariance = 0;

    private Instant directionChangedAt;

    private Instant lastSegmentDepartureAt;

    private String lastSegmentDepartureStopId;

}
