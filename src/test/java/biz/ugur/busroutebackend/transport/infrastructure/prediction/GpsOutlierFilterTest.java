package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GpsOutlierFilterTest {

    private GpsOutlierFilter filter;

    @BeforeEach
    void setUp() {
        filter = new GpsOutlierFilter(new PredictionProperties());
    }

    @Test
    void acceptsWhenNoPreviousState() {
        GpsOutlierFilter.Decision decision = filter.evaluate(
                null, 37.96, 58.33, Instant.now(), "v-1", "1234 AGH");
        assertEquals(GpsOutlierFilter.Decision.ACCEPT, decision);
    }

    @Test
    void acceptsWhenElapsedIsZeroOrNegative() {
        Instant now = Instant.now();
        VehiclePredictionState state = VehiclePredictionState.builder()
                .gpsLatitude(37.96).gpsLongitude(58.33)
                .lastGpsUpdate(now)
                .build();

        GpsOutlierFilter.Decision decision = filter.evaluate(
                state, 37.961, 58.331, now, "v-1", "1234 AGH");

        assertEquals(GpsOutlierFilter.Decision.ACCEPT, decision);
    }

    @Test
    void rejectsHardOutlierFor15kmLeapIn2s() {
        Instant before = Instant.ofEpochMilli(1_000_000);
        Instant after = Instant.ofEpochMilli(1_002_000);

        VehiclePredictionState state = VehiclePredictionState.builder()
                .gpsLatitude(37.96).gpsLongitude(58.33)
                .lastGpsUpdate(before)
                .build();

        GpsOutlierFilter.Decision decision = filter.evaluate(
                state, 38.10, 58.50, after, "v-1", "1234 AGH");

        assertEquals(GpsOutlierFilter.Decision.REJECT_HARD_OUTLIER, decision);
    }

    @Test
    void rejectsSoftOutlierForSlowBusGoingTooFar() {
        Instant before = Instant.ofEpochMilli(1_000_000);
        Instant after = Instant.ofEpochMilli(1_010_000);

        VehiclePredictionState state = VehiclePredictionState.builder()
                .gpsLatitude(37.96).gpsLongitude(58.33)
                .lastGpsUpdate(before)
                .build();

        GpsOutlierFilter.Decision decision = filter.evaluate(
                state, 38.00, 58.37, after, "v-1", "1234 AGH");

        assertEquals(GpsOutlierFilter.Decision.REJECT_SOFT_OUTLIER, decision);
    }

    @Test
    void acceptsRealisticMovement() {
        Instant before = Instant.ofEpochMilli(1_000_000);
        Instant after = Instant.ofEpochMilli(1_030_000);

        VehiclePredictionState state = VehiclePredictionState.builder()
                .gpsLatitude(37.96).gpsLongitude(58.33)
                .lastGpsUpdate(before)
                .build();

        GpsOutlierFilter.Decision decision = filter.evaluate(
                state, 37.961, 58.331, after, "v-1", "1234 AGH");

        assertEquals(GpsOutlierFilter.Decision.ACCEPT, decision);
    }

    @Test
    void rejectsTeleportAfterLongGap() {
        Instant before = Instant.ofEpochMilli(1_000_000);
        Instant after = Instant.ofEpochMilli(1_600_000);

        VehiclePredictionState state = VehiclePredictionState.builder()
                .gpsLatitude(37.96).gpsLongitude(58.33)
                .lastGpsUpdate(before)
                .build();

        GpsOutlierFilter.Decision decision = filter.evaluate(
                state, 38.10, 58.50, after, "v-1", "1234 AGH");

        assertEquals(GpsOutlierFilter.Decision.REJECT_TELEPORT_GAP, decision);
    }
}
