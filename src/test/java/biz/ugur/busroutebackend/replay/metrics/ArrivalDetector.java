package biz.ugur.busroutebackend.replay.metrics;

import biz.ugur.busroutebackend.replay.GeometryFixture;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class ArrivalDetector {

    public record Config(double arrZoneMeters, double arrSpeedKmh) {}

    public record RawPoint(Instant time, double latitude, double longitude, double speedKmh) {}

    private final Config config;

    public ArrivalDetector(Config config) {
        this.config = config;
    }

    public Optional<Instant> detectArrival(List<RawPoint> rawTrack, double stopLat, double stopLon) {
        for (RawPoint p : rawTrack) {
            double d = GeometryFixture.haversineMeters(p.latitude(), p.longitude(), stopLat, stopLon);
            if (d <= config.arrZoneMeters() && p.speedKmh() < config.arrSpeedKmh()) {
                return Optional.of(p.time());
            }
        }
        return Optional.empty();
    }
}
