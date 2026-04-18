package biz.ugur.busroutebackend.shared.test.integration;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record GpsFixture(
        @JsonProperty("vehicleId") String vehicleId,
        @JsonProperty("licensePlate") String licensePlate,
        @JsonProperty("routeNumber") String routeNumber,
        @JsonProperty("latitude") double latitude,
        @JsonProperty("longitude") double longitude,
        @JsonProperty("speedKmh") double speedKmh,
        @JsonProperty("course") double course,
        @JsonProperty("inMotion") boolean inMotion,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("direction") int direction,
        @JsonProperty("wallClock") Instant wallClock
) {
}
