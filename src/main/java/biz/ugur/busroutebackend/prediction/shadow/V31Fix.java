package biz.ugur.busroutebackend.prediction.shadow;

import java.time.Instant;

public record V31Fix(String vehicleId, String licensePlate, String routeNumber,
                     double latitude, double longitude, double speedKmh, double course,
                     boolean inMotion, Instant timestamp, int direction) {}
