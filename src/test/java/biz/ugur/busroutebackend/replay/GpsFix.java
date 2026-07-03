package biz.ugur.busroutebackend.replay;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"vehicleId", "licensePlate", "routeNumber", "latitude", "longitude",
        "speedKmh", "course", "inMotion", "timestamp", "direction",
        "hdop", "satellites", "accuracy", "wallClock"})
public record GpsFix(
        String vehicleId,
        String licensePlate,
        String routeNumber,
        double latitude,
        double longitude,
        double speedKmh,
        double course,
        boolean inMotion,
        Instant timestamp,
        int direction,
        Double hdop,
        Integer satellites,
        Double accuracy,
        Instant wallClock) {
}
