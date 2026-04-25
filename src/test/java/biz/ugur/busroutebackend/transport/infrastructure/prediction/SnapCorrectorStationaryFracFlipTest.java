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
class SnapCorrectorStationaryFracFlipTest {

    private static final String ROUTE = "160";
    private static final String VEHICLE_ID = "veh-1";
    private static final String PLATE = "6235 AGJ";

    private static final double FORWARD_LAT = 37.90976;
    private static final double FORWARD_LON = 58.38966;
    private static final double BACKWARD_LAT = 37.90877;
    private static final double BACKWARD_LON = 58.38902;

    @Mock
    private RouteGeometryCache routeGeometryCache;

    @Mock
    private MapMatchingService mapMatchingService;

    private PredictionProperties properties;
    private SnapCorrector snapCorrector;

    @BeforeEach
    void setUp() {
        properties = new PredictionProperties();
        snapCorrector = new SnapCorrector(properties, routeGeometryCache, mapMatchingService);

        List<double[]> forwardCoords = List.of(new double[]{FORWARD_LAT, FORWARD_LON});
        List<double[]> backwardCoords = List.of(new double[]{BACKWARD_LAT, BACKWARD_LON});

        lenient().when(routeGeometryCache.getPoints(ROUTE, 0)).thenReturn(forwardCoords);
        lenient().when(routeGeometryCache.getPoints(ROUTE, 1)).thenReturn(backwardCoords);
        lenient().when(routeGeometryCache.getTotalDistance(eq(ROUTE), anyInt())).thenReturn(22_000.0);
        lenient().when(routeGeometryCache.getCumulativeDistances(eq(ROUTE), anyInt())).thenReturn(null);
        lenient().when(mapMatchingService.calculateCourseFromRoute(any(), any(), anyDouble(), anyInt(), anyDouble()))
                .thenReturn(90.0);
    }

    @Test
    void filtersFlipUsingInMotionFlag_NotKalman() {
        VehiclePredictionState existing = VehiclePredictionState.builder()
                .vehicleId(VEHICLE_ID)
                .licensePlate(PLATE)
                .routeNumber(ROUTE)
                .direction(1)
                .predictedLatitude(FORWARD_LAT)
                .predictedLongitude(FORWARD_LON)
                .fractionOnRoute(0.4544)
                .lastGpsFraction(0.4544)
                .inMotion(false)
                .rawGpsSpeedKmh(0.0)
                .kalmanSpeedKmh(18.0)
                .build();

        MapMatchingService.SnappedResult forwardSnap =
                new MapMatchingService.SnappedResult(FORWARD_LAT, FORWARD_LON, 0.4483, 5.5, true);
        when(mapMatchingService.snapToNearestSegment(anyDouble(), anyDouble(), any(), any(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(forwardSnap);

        SnapCorrector.SnapResult result = snapCorrector.applySnap(
                existing, VEHICLE_ID, PLATE, ROUTE,
                FORWARD_LAT, FORWARD_LON, 0.0, 1);

        assertThat(result.direction())
                .as("kalmanSpeedKmh=18 still high (slow Kalman convergence after stop), " +
                    "but inMotion=false says bus is stationary — flip MUST be suppressed " +
                    "(this is the 6235 AGJ case at 09:24:13)")
                .isEqualTo(1);
    }

    @Test
    void allowsFlipWhenActuallyMoving() {
        VehiclePredictionState existing = VehiclePredictionState.builder()
                .vehicleId(VEHICLE_ID)
                .licensePlate(PLATE)
                .routeNumber(ROUTE)
                .direction(1)
                .predictedLatitude(FORWARD_LAT)
                .predictedLongitude(FORWARD_LON)
                .fractionOnRoute(0.4544)
                .lastGpsFraction(0.4544)
                .inMotion(true)
                .rawGpsSpeedKmh(20.0)
                .kalmanSpeedKmh(20.0)
                .build();

        MapMatchingService.SnappedResult forwardSnap =
                new MapMatchingService.SnappedResult(FORWARD_LAT, FORWARD_LON, 0.4483, 5.5, true);
        MapMatchingService.SnappedResult backwardSnap =
                new MapMatchingService.SnappedResult(FORWARD_LAT + 0.0001, FORWARD_LON + 0.0001, 0.5587, 5.0, true);

        when(mapMatchingService.snapToNearestSegment(anyDouble(), anyDouble(), any(), any(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(forwardSnap);
        when(mapMatchingService.snapToNearestSegment(anyDouble(), anyDouble(), any(), anyDouble()))
                .thenReturn(backwardSnap);

        SnapCorrector.SnapResult result = snapCorrector.applySnap(
                existing, VEHICLE_ID, PLATE, ROUTE,
                FORWARD_LAT, FORWARD_LON, 0.0, 1);

        assertThat(result.direction())
                .as("inMotion=true rawGpsSpeed=20 — bus is actually moving, " +
                    "flip path is preserved (legacy behavior unchanged)")
                .isEqualTo(0);
    }

    @Test
    void filtersFlipWhenInMotionTrueButRawSpeedNearZero() {
        VehiclePredictionState existing = VehiclePredictionState.builder()
                .vehicleId(VEHICLE_ID)
                .licensePlate(PLATE)
                .routeNumber(ROUTE)
                .direction(1)
                .predictedLatitude(FORWARD_LAT)
                .predictedLongitude(FORWARD_LON)
                .fractionOnRoute(0.4544)
                .lastGpsFraction(0.4544)
                .inMotion(true)
                .rawGpsSpeedKmh(1.0)
                .kalmanSpeedKmh(15.0)
                .build();

        MapMatchingService.SnappedResult forwardSnap =
                new MapMatchingService.SnappedResult(FORWARD_LAT, FORWARD_LON, 0.4483, 5.5, true);
        when(mapMatchingService.snapToNearestSegment(anyDouble(), anyDouble(), any(), any(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(forwardSnap);

        SnapCorrector.SnapResult result = snapCorrector.applySnap(
                existing, VEHICLE_ID, PLATE, ROUTE,
                FORWARD_LAT, FORWARD_LON, 0.0, 1);

        assertThat(result.direction())
                .as("rawGpsSpeed=1 km/h falls below 5 km/h floor — treated as stationary " +
                    "even when inMotion flag has not flipped yet (e.g. just braking)")
                .isEqualTo(1);
    }
}
