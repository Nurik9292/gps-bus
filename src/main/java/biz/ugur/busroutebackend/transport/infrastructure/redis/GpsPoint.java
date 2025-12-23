package biz.ugur.busroutebackend.transport.infrastructure.redis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Represents a GPS point stored in Redis for route detection.
 * Optimized for minimal storage size.
 */
@Getter
public class GpsPoint {
    private final double lat;
    private final double lon;
    private final double speed;
    private final LocalDateTime time;

    @JsonCreator
    public GpsPoint(
            @JsonProperty("lat") double lat,
            @JsonProperty("lon") double lon,
            @JsonProperty("speed") double speed,
            @JsonProperty("time") LocalDateTime time) {
        this.lat = lat;
        this.lon = lon;
        this.speed = speed;
        this.time = time;
    }

    public static GpsPoint of(Double latitude, Double longitude, Double speed, LocalDateTime timestamp) {
        return new GpsPoint(
                latitude != null ? latitude : 0.0,
                longitude != null ? longitude : 0.0,
                speed != null ? speed : 0.0,
                timestamp != null ? timestamp : LocalDateTime.now()
        );
    }

    @Override
    public String toString() {
        return String.format("GpsPoint[lat=%.6f, lon=%.6f, speed=%.1f, time=%s]",
                lat, lon, speed, time);
    }
}
