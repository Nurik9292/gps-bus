package biz.ugur.busroutebackend.prediction.shadow;

import java.time.Instant;

public record V31Fix(String vehicleId, String licensePlate, String routeNumber,
                     String routeId,
                     double latitude, double longitude, double speedKmh, double course,
                     boolean inMotion, Instant timestamp, int direction,
                     Double hdop, Integer satellites, Double accuracy) {

    public V31Fix(String vehicleId, String licensePlate, String routeNumber,
                  double latitude, double longitude, double speedKmh, double course,
                  boolean inMotion, Instant timestamp, int direction,
                  Double hdop, Integer satellites, Double accuracy) {
        this(vehicleId, licensePlate, routeNumber, null, latitude, longitude, speedKmh,
                course, inMotion, timestamp, direction, hdop, satellites, accuracy);
    }

    public String routeCacheKey() {
        return routeId != null ? routeId : routeNumber;
    }
}
