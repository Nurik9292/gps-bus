package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnapCorrectorOppositeFallbackTest {

    private static final String ROUTE = "160";
    private static final String VEHICLE_ID = "veh-1";
    private static final String PLATE = "6599 AGJ";

    private static final double PRED_LAT = 37.91000;
    private static final double PRED_LON = 58.35100;

    private static final double GPS_LAT = 37.90990;
    private static final double GPS_LON = 58.35085;

    private static final double OPPOSITE_SNAP_LAT = 37.90000;
    private static final double OPPOSITE_SNAP_LON = 58.34979;

    @Mock
    private RouteGeometryCache routeGeometryCache;

    @Mock
    private MapMatchingService mapMatchingService;

    private PredictionProperties properties;
    private SnapCorrector snapCorrector;

    @BeforeEach
    void setUp() {
        properties = new PredictionProperties();
        snapCorrector = new SnapCorrector(properties, routeGeometryCache, mapMatchingService, new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.DirectionChangeCooldown(properties), new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.PlausibilityChecker(properties), new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.ConsecutiveOppositeCounter(), new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.OppositeFallbackStrategy(properties, routeGeometryCache, mapMatchingService, new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.PlausibilityChecker(properties), new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.ConsecutiveOppositeCounter()), new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.HeadingFlipStrategy(properties, routeGeometryCache, mapMatchingService, new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.DirectionChangeCooldown(properties), new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.PlausibilityChecker(properties)), new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.FracFlipStrategy(properties, routeGeometryCache, mapMatchingService, new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.DirectionChangeCooldown(properties), new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.PlausibilityChecker(properties)), new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.ImplausibleJumpHandler(), new biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer());

        List<double[]> forwardCoords = List.of(new double[]{PRED_LAT, PRED_LON});
        List<double[]> backwardCoords = List.of(new double[]{OPPOSITE_SNAP_LAT, OPPOSITE_SNAP_LON});

        lenient().when(routeGeometryCache.getPoints(ROUTE, 0)).thenReturn(forwardCoords);
        lenient().when(routeGeometryCache.getPoints(ROUTE, 1)).thenReturn(backwardCoords);
        lenient().when(routeGeometryCache.getTotalDistance(eq(ROUTE), anyInt())).thenReturn(10_000.0);
        lenient().when(routeGeometryCache.getCumulativeDistances(eq(ROUTE), anyInt())).thenReturn(null);
    }

    @Test
    void rejectsOppositeFallbackWhenPhysicalJumpExceedsDirectionFlipMax() {
        VehiclePredictionState existing = VehiclePredictionState.builder()
                .vehicleId(VEHICLE_ID)
                .licensePlate(PLATE)
                .routeNumber(ROUTE)
                .direction(0)
                .predictedLatitude(PRED_LAT)
                .predictedLongitude(PRED_LON)
                .fractionOnRoute(0.5)
                .lastGpsFraction(0.5)
                .build();

        MapMatchingService.SnappedResult primaryFail =
                new MapMatchingService.SnappedResult(GPS_LAT, GPS_LON, -1, Double.MAX_VALUE, false);
        MapMatchingService.SnappedResult oppositeOk =
                new MapMatchingService.SnappedResult(OPPOSITE_SNAP_LAT, OPPOSITE_SNAP_LON, 0.55, 80.0, true);

        when(mapMatchingService.snapToNearestSegment(anyDouble(), anyDouble(), any(), any(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(primaryFail);
        when(mapMatchingService.snapToNearestSegment(anyDouble(), anyDouble(), any(), anyDouble()))
                .thenReturn(oppositeOk);

        SnapCorrector.SnapResult result = snapCorrector.applySnap(
                existing, VEHICLE_ID, PLATE, ROUTE,
                GPS_LAT, GPS_LON, 126.0, 0);

        assertThat(result.predictedLatitude()).isEqualTo(PRED_LAT);
        assertThat(result.predictedLongitude()).isEqualTo(PRED_LON);
        assertThat(result.direction()).isEqualTo(0);
        assertThat(result.fraction()).isEqualTo(0.5);
    }

    @Test
    void acceptsOppositeFallbackWhenPhysicalJumpWithinLimit() {
        double nearPredictedLat = PRED_LAT + 0.0005;
        double nearPredictedLon = PRED_LON + 0.0005;

        VehiclePredictionState existing = VehiclePredictionState.builder()
                .vehicleId(VEHICLE_ID)
                .licensePlate(PLATE)
                .routeNumber(ROUTE)
                .direction(0)
                .predictedLatitude(PRED_LAT)
                .predictedLongitude(PRED_LON)
                .fractionOnRoute(0.5)
                .lastGpsFraction(0.5)
                .build();

        MapMatchingService.SnappedResult primaryFail =
                new MapMatchingService.SnappedResult(GPS_LAT, GPS_LON, -1, Double.MAX_VALUE, false);
        MapMatchingService.SnappedResult oppositeClose =
                new MapMatchingService.SnappedResult(nearPredictedLat, nearPredictedLon, 0.5, 30.0, true);

        when(mapMatchingService.snapToNearestSegment(anyDouble(), anyDouble(), any(), any(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(primaryFail);
        when(mapMatchingService.snapToNearestSegment(anyDouble(), anyDouble(), any(), anyDouble()))
                .thenReturn(oppositeClose);

        SnapCorrector.SnapResult result = snapCorrector.applySnap(
                existing, VEHICLE_ID, PLATE, ROUTE,
                GPS_LAT, GPS_LON, 126.0, 0);

        assertThat(result.predictedLatitude()).isEqualTo(nearPredictedLat);
        assertThat(result.predictedLongitude()).isEqualTo(nearPredictedLon);
        assertThat(result.direction()).isEqualTo(1);
    }
}
