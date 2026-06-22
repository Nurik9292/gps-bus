package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.ConsecutiveOppositeCounter;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.DirectionChangeCooldown;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.FracFlipStrategy;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.HeadingFlipStrategy;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.ImplausibleJumpHandler;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.OppositeFallbackStrategy;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.PlausibilityChecker;
import biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnapCorrectorStaleAnchorTest {

    private static final String ROUTE = "160";
    private static final String VEHICLE_ID = "veh-stale";
    private static final String PLATE = "0001 AG";

    private static final double GPS_LAT = 37.90000;
    private static final double GPS_LON = 58.30000;

    private static final double WINDOWED_LAT = 37.90011;
    private static final double WHOLE_LINE_LAT = 37.90022;
    private static final double SNAP_LON = 58.30000;
    private static final double SNAP_FRACTION = 0.30;

    private final Instant fixTime = Instant.parse("2026-06-22T08:00:00Z");

    @Mock
    private RouteGeometryCache routeGeometryCache;

    @Mock
    private MapMatchingService mapMatchingService;

    private PredictionProperties properties;
    private SnapCorrector snapCorrector;

    @BeforeEach
    void setUp() {
        properties = new PredictionProperties();
        snapCorrector = new SnapCorrector(properties, routeGeometryCache, mapMatchingService,
                new DirectionChangeCooldown(properties),
                new PlausibilityChecker(properties),
                new ConsecutiveOppositeCounter(),
                new OppositeFallbackStrategy(properties, routeGeometryCache, mapMatchingService,
                        new PlausibilityChecker(properties), new ConsecutiveOppositeCounter()),
                new HeadingFlipStrategy(properties, routeGeometryCache, mapMatchingService,
                        new DirectionChangeCooldown(properties), new PlausibilityChecker(properties)),
                new FracFlipStrategy(properties, routeGeometryCache, mapMatchingService,
                        new DirectionChangeCooldown(properties), new PlausibilityChecker(properties)),
                new ImplausibleJumpHandler(),
                new PipelineTracer());

        List<double[]> forwardCoords = List.of(
                new double[]{37.90000, 58.30000},
                new double[]{37.91000, 58.30000});

        lenient().when(routeGeometryCache.getPoints(eq(ROUTE), eq(0))).thenReturn(forwardCoords);
        lenient().when(routeGeometryCache.getTotalDistance(eq(ROUTE), anyInt())).thenReturn(10_000.0);
        lenient().when(routeGeometryCache.getCumulativeDistances(eq(ROUTE), anyInt())).thenReturn(null);

        MapMatchingService.SnappedResult windowedSnap =
                new MapMatchingService.SnappedResult(WINDOWED_LAT, SNAP_LON, SNAP_FRACTION, 10.0, true);
        MapMatchingService.SnappedResult wholeLineSnap =
                new MapMatchingService.SnappedResult(WHOLE_LINE_LAT, SNAP_LON, SNAP_FRACTION, 10.0, true);

        lenient().when(mapMatchingService.snapToNearestSegment(
                        anyDouble(), anyDouble(), any(), any(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(windowedSnap);
        lenient().when(mapMatchingService.snapToNearestSegment(anyDouble(), anyDouble(), any(), anyDouble()))
                .thenReturn(wholeLineSnap);
        lenient().when(mapMatchingService.calculateCourseFromRoute(any(), any(), anyDouble(), anyInt(), anyDouble()))
                .thenReturn(0.0);
    }

    private VehiclePredictionState.VehiclePredictionStateBuilder anchorBaseline() {
        return VehiclePredictionState.builder()
                .vehicleId(VEHICLE_ID)
                .licensePlate(PLATE)
                .routeNumber(ROUTE)
                .direction(0)
                .predictedLatitude(37.90005)
                .predictedLongitude(58.30000)
                .fractionOnRoute(0.25)
                .lastGpsFraction(0.25)
                .inMotion(true)
                .rawGpsSpeedKmh(20.0);
    }

    @Test
    void usesWindowedSnapWhenAnchorIsFresh() {
        VehiclePredictionState existing = anchorBaseline()
                .lastGpsUpdate(fixTime.minusSeconds(5))
                .build();

        SnapCorrector.SnapResult result = snapCorrector.applySnap(
                existing, VEHICLE_ID, PLATE, ROUTE,
                GPS_LAT, GPS_LON, 0.0, 0, fixTime);

        assertThat(result.predictedLatitude())
                .as("a fresh anchor must keep the windowed snap around last_s")
                .isEqualTo(WINDOWED_LAT);
    }

    @Test
    void fallsBackToWholeLineSnapWhenAnchorIsStale() {
        VehiclePredictionState existing = anchorBaseline()
                .lastGpsUpdate(fixTime.minusSeconds(1801))
                .build();

        SnapCorrector.SnapResult result = snapCorrector.applySnap(
                existing, VEHICLE_ID, PLATE, ROUTE,
                GPS_LAT, GPS_LON, 0.0, 0, fixTime);

        assertThat(result.predictedLatitude())
                .as("a stale anchor (older than staleAnchorMs) must force a cold whole-line snap, ignoring last_s")
                .isEqualTo(WHOLE_LINE_LAT);
    }
}
