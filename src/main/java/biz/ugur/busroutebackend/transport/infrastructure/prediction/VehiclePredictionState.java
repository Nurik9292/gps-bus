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

    @Builder.Default
    private int direction = 0;

}
