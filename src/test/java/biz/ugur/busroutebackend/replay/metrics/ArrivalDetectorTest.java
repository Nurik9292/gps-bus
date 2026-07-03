package biz.ugur.busroutebackend.replay.metrics;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ArrivalDetectorTest {

    private static final double STOP_LAT = 38.0500;
    private static final double STOP_LON = 58.1700;

    private final ArrivalDetector detector =
            new ArrivalDetector(new ArrivalDetector.Config(50.0, 5.0));

    private ArrivalDetector.RawPoint pt(String t, double lat, double lon, double kmh) {
        return new ArrivalDetector.RawPoint(Instant.parse(t), lat, lon, kmh);
    }

    @Test
    void detectsFirstSlowEntryIntoStopZone() {
        List<ArrivalDetector.RawPoint> track = List.of(
                pt("2026-07-03T10:00:00Z", 38.0450, 58.1700, 30),
                pt("2026-07-03T10:00:10Z", 38.0480, 58.1700, 25),
                pt("2026-07-03T10:00:20Z", 38.0497, 58.1700, 12),
                pt("2026-07-03T10:00:30Z", 38.0499, 58.1700, 3),
                pt("2026-07-03T10:00:40Z", 38.0500, 58.1700, 0),
                pt("2026-07-03T10:01:00Z", 38.0505, 58.1700, 15));

        Optional<Instant> arrival = detector.detectArrival(track, STOP_LAT, STOP_LON);

        assertThat(arrival).contains(Instant.parse("2026-07-03T10:00:30Z"));
    }

    @Test
    void fastDriveThroughZoneIsNotArrival() {
        List<ArrivalDetector.RawPoint> track = List.of(
                pt("2026-07-03T10:00:00Z", 38.0490, 58.1700, 40),
                pt("2026-07-03T10:00:05Z", 38.0500, 58.1700, 38),
                pt("2026-07-03T10:00:10Z", 38.0510, 58.1700, 41));

        assertThat(detector.detectArrival(track, STOP_LAT, STOP_LON)).isEmpty();
    }

    @Test
    void slowPointOutsideZoneIsNotArrival() {
        List<ArrivalDetector.RawPoint> track = List.of(
                pt("2026-07-03T10:00:00Z", 38.0400, 58.1700, 2));

        assertThat(detector.detectArrival(track, STOP_LAT, STOP_LON)).isEmpty();
    }
}
