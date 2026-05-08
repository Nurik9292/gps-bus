package biz.ugur.busroutebackend.transport.infrastructure.prediction.snap;

import biz.ugur.busroutebackend.transport.infrastructure.prediction.MapMatchingService;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.PredictionProperties;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.RouteGeometryCache;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePredictionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OppositeFallbackStrategyTest {

    private static final String VEHICLE = "veh-1";
    private static final String PLATE = "AA-001";
    private static final String ROUTE = "14";

    private static final double LAT = 37.9;
    private static final double LON = 58.3;

    @Mock
    private RouteGeometryCache routeGeometryCache;

    @Mock
    private MapMatchingService mapMatchingService;

    private PredictionProperties properties;
    private OppositeFallbackStrategy strategy;
    private ConsecutiveOppositeCounter counter;

    @BeforeEach
    void setUp() {
        properties = new PredictionProperties();
        counter = new ConsecutiveOppositeCounter();
        strategy = new OppositeFallbackStrategy(
                properties,
                routeGeometryCache,
                mapMatchingService,
                new PlausibilityChecker(properties),
                counter);

        List<double[]> coords = List.of(new double[]{LAT, LON}, new double[]{LAT + 0.001, LON + 0.001});
        lenient().when(routeGeometryCache.getPoints(eq(ROUTE), anyInt())).thenReturn(coords);
        lenient().when(routeGeometryCache.getTotalDistance(eq(ROUTE), anyInt())).thenReturn(10_000.0);
    }

    private VehiclePredictionState baseline() {
        return VehiclePredictionState.builder()
                .vehicleId(VEHICLE)
                .licensePlate(PLATE)
                .routeNumber(ROUTE)
                .direction(0)
                .predictedLatitude(LAT)
                .predictedLongitude(LON)
                .fractionOnRoute(0.5)
                .lastGpsFraction(0.5)
                .build();
    }

    private MapMatchingService.SnappedResult snap(double lat, double lon, double frac, double distance) {
        return new MapMatchingService.SnappedResult(lat, lon, frac, distance, true);
    }

    @Test
    void hardMismatchQueuesDirectionFixOnFirstObservation() {
        MapMatchingService.SnappedResult primary = snap(LAT, LON, 0.5, 250.0);
        MapMatchingService.SnappedResult opposite = snap(LAT, LON, 0.5, 30.0);
        when(mapMatchingService.snapToNearestSegment(anyDouble(), anyDouble(), any(), anyDouble()))
                .thenReturn(opposite);

        OppositeFallbackStrategy.Result result = strategy.tryFlip(
                baseline(), VEHICLE, PLATE, ROUTE, 0, LAT, LON, primary, primary.distanceMeters());

        assertThat(result.accepted()).isTrue();
        assertThat(result.direction()).isEqualTo(1);

        Map<String, Integer> queued = counter.drainPendingDirectionFixes();
        assertThat(queued)
                .as("hard mismatch (primary 250 m + opposite 30 m) should fast-path the direction fix on the first observation")
                .containsEntry(VEHICLE, 1);
    }

    @Test
    void softMismatchStillRequiresThreeConsecutiveObservations() {
        MapMatchingService.SnappedResult primary = snap(LAT, LON, 0.5, 80.0);
        MapMatchingService.SnappedResult opposite = snap(LAT, LON, 0.5, 60.0);
        when(mapMatchingService.snapToNearestSegment(anyDouble(), anyDouble(), any(), anyDouble()))
                .thenReturn(opposite);

        for (int i = 0; i < 2; i++) {
            strategy.tryFlip(baseline(), VEHICLE, PLATE, ROUTE, 0, LAT, LON, primary, primary.distanceMeters());
            assertThat(counter.drainPendingDirectionFixes())
                    .as("soft mismatch (no hard primary>200/opposite<50) must not fast-path on observation #%d", i + 1)
                    .isEmpty();
        }

        strategy.tryFlip(baseline(), VEHICLE, PLATE, ROUTE, 0, LAT, LON, primary, primary.distanceMeters());

        assertThat(counter.drainPendingDirectionFixes())
                .as("third soft observation triggers the standard threshold (oppositeSnapThreshold=3)")
                .containsEntry(VEHICLE, 1);
    }

    @Test
    void hardMismatchWithThresholdRaisedToHigherDoesNotShortCircuit() {
        properties.setOppositeSnapHardThreshold(2);

        MapMatchingService.SnappedResult primary = snap(LAT, LON, 0.5, 250.0);
        MapMatchingService.SnappedResult opposite = snap(LAT, LON, 0.5, 30.0);
        when(mapMatchingService.snapToNearestSegment(anyDouble(), anyDouble(), any(), anyDouble()))
                .thenReturn(opposite);

        strategy.tryFlip(baseline(), VEHICLE, PLATE, ROUTE, 0, LAT, LON, primary, primary.distanceMeters());
        assertThat(counter.drainPendingDirectionFixes())
                .as("hard threshold = 2 should not fast-path on the first observation")
                .isEmpty();

        strategy.tryFlip(baseline(), VEHICLE, PLATE, ROUTE, 0, LAT, LON, primary, primary.distanceMeters());
        assertThat(counter.drainPendingDirectionFixes())
                .as("second hard observation flushes once threshold is met")
                .containsEntry(VEHICLE, 1);
    }
}
