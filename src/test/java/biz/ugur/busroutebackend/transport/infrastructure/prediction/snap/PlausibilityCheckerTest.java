package biz.ugur.busroutebackend.transport.infrastructure.prediction.snap;

import biz.ugur.busroutebackend.transport.infrastructure.prediction.MapMatchingService;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.PredictionProperties;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePredictionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlausibilityCheckerTest {

    private static final double LAT = 37.9;
    private static final double LON = 58.3;

    private PredictionProperties props;
    private PlausibilityChecker checker;

    @BeforeEach
    void setUp() {
        props = new PredictionProperties();
        checker = new PlausibilityChecker(props);
    }

    private VehiclePredictionState state(double predLat, double predLon, double frac) {
        return VehiclePredictionState.builder()
                .vehicleId("v1")
                .predictedLatitude(predLat)
                .predictedLongitude(predLon)
                .fractionOnRoute(frac)
                .build();
    }

    private MapMatchingService.SnappedResult snap(double lat, double lon, double frac) {
        return new MapMatchingService.SnappedResult(lat, lon, frac, 5.0, true);
    }

    @Test
    void allowsFlipWhenExistingHasNoPredictedPosition() {
        VehiclePredictionState fresh = VehiclePredictionState.builder().vehicleId("v1").build();
        assertThat(checker.isDirectionFlipPhysicallyPlausible(
                "v1", "HEADING", fresh, snap(LAT, LON, 0.5), 0.5)).isTrue();
    }

    @Test
    void midRouteRejectsFlipWhenPhysicalJumpExceedsThreshold() {
        VehiclePredictionState s = state(LAT, LON, 0.5);
        double farLat = LAT + 0.005;
        boolean ok = checker.isDirectionFlipPhysicallyPlausible(
                "v1", "HEADING", s, snap(farLat, LON, 0.5), 0.5);
        assertThat(ok).isFalse();
    }

    @Test
    void midRouteAllowsFlipWhenPhysicalJumpWithinThreshold() {
        VehiclePredictionState s = state(LAT, LON, 0.5);
        double nearLat = LAT + 0.0001;
        boolean ok = checker.isDirectionFlipPhysicallyPlausible(
                "v1", "HEADING", s, snap(nearLat, LON, 0.5), 0.5);
        assertThat(ok).isTrue();
    }

    @Test
    void terminalAllowsLargerJumpThanMidRoute() {
        VehiclePredictionState s = state(LAT, LON, 0.02);
        double midRangeLat = LAT + 0.0025;
        boolean ok = checker.isDirectionFlipPhysicallyPlausible(
                "v1", "HEADING", s, snap(midRangeLat, LON, 0.98), 0.98);
        assertThat(ok).isTrue();
    }

    @Test
    void terminalRejectsExcessivePhysicalJump() {
        VehiclePredictionState s = state(LAT, LON, 0.02);
        double farLat = LAT + 0.01;
        boolean ok = checker.isDirectionFlipPhysicallyPlausible(
                "v1", "HEADING", s, snap(farLat, LON, 0.98), 0.98);
        assertThat(ok).isFalse();
    }
}
