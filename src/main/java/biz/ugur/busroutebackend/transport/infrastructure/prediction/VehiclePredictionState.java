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

}
