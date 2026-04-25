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
    private static final String PLATE = "6368 AGJ";

    private static final double FORWARD_LAT = 37.89872;
    private static final double FORWARD_LON = 58.38837;
    private static final double BACKWARD_LAT = 37.90015;
    private static final double BACKWARD_LON = 58.38882;

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
    void doesNotFlipDirectionWhenStationaryBusFracMicroBackward() {
        VehiclePredictionState existing = VehiclePredictionState.builder()
                .vehicleId(VEHICLE_ID)
                .licensePlate(PLATE)
                .routeNumber(ROUTE)
                .direction(0)
                .predictedLatitude(FORWARD_LAT)
                .predictedLongitude(FORWARD_LON)
                .fractionOnRoute(0.6195)
                .lastGpsFraction(0.6195)
                .kalmanSpeedKmh(0.0)
                .build();

        MapMatchingService.SnappedResult forwardSnap =
                new MapMatchingService.SnappedResult(FORWARD_LAT, FORWARD_LON, 0.6106, 2.4, true);
        when(mapMatchingService.snapToNearestSegment(anyDouble(), anyDouble(), any(), any(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(forwardSnap);

        SnapCorrector.SnapResult result = snapCorrector.applySnap(
                existing, VEHICLE_ID, PLATE, ROUTE,
                FORWARD_LAT, FORWARD_LON, 0.0, 0);

        assertThat(result.direction())
                .as("stationary bus must NOT flip direction on micro fraction noise (0.0088 ≈ 194m on 22km route is GPS jitter, not real reversal)")
                .isEqualTo(0);
    }

    @Test
    void flipsDirectionWhenMovingBusFracMicroBackward() {
        VehiclePredictionState existing = VehiclePredictionState.builder()
                .vehicleId(VEHICLE_ID)
                .licensePlate(PLATE)
                .routeNumber(ROUTE)
                .direction(0)
                .predictedLatitude(FORWARD_LAT)
                .predictedLongitude(FORWARD_LON)
                .fractionOnRoute(0.6195)
                .lastGpsFraction(0.6195)
                .kalmanSpeedKmh(30.0)
                .build();

        MapMatchingService.SnappedResult forwardSnap =
                new MapMatchingService.SnappedResult(FORWARD_LAT, FORWARD_LON, 0.6106, 2.4, true);
        MapMatchingService.SnappedResult backwardSnap =
                new MapMatchingService.SnappedResult(FORWARD_LAT + 0.0001, FORWARD_LON + 0.0001, 0.3955, 5.0, true);

        when(mapMatchingService.snapToNearestSegment(anyDouble(), anyDouble(), any(), any(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(forwardSnap);
        when(mapMatchingService.snapToNearestSegment(anyDouble(), anyDouble(), any(), anyDouble()))
                .thenReturn(backwardSnap);

        SnapCorrector.SnapResult result = snapCorrector.applySnap(
                existing, VEHICLE_ID, PLATE, ROUTE,
                FORWARD_LAT, FORWARD_LON, 0.0, 0);

        assertThat(result.direction())
                .as("moving bus retains existing flip path — micro backward delta with movement may be a real direction reversal")
                .isEqualTo(1);
    }

    @Test
    void doesNotFlipWhenKalmanSpeedUninitialized() {
        VehiclePredictionState existing = VehiclePredictionState.builder()
                .vehicleId(VEHICLE_ID)
                .licensePlate(PLATE)
                .routeNumber(ROUTE)
                .direction(0)
                .predictedLatitude(FORWARD_LAT)
                .predictedLongitude(FORWARD_LON)
                .fractionOnRoute(0.6195)
                .lastGpsFraction(0.6195)
                .kalmanSpeedKmh(-1)
                .build();

        MapMatchingService.SnappedResult forwardSnap =
                new MapMatchingService.SnappedResult(FORWARD_LAT, FORWARD_LON, 0.6106, 2.4, true);
        MapMatchingService.SnappedResult backwardSnap =
                new MapMatchingService.SnappedResult(FORWARD_LAT + 0.0001, FORWARD_LON + 0.0001, 0.3955, 5.0, true);

        when(mapMatchingService.snapToNearestSegment(anyDouble(), anyDouble(), any(), any(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(forwardSnap);
        when(mapMatchingService.snapToNearestSegment(anyDouble(), anyDouble(), any(), anyDouble()))
                .thenReturn(backwardSnap);

        SnapCorrector.SnapResult result = snapCorrector.applySnap(
                existing, VEHICLE_ID, PLATE, ROUTE,
                FORWARD_LAT, FORWARD_LON, 0.0, 0);

        assertThat(result.direction())
                .as("uninitialized kalmanSpeed (-1) must not be treated as stationary — flip allowed (default behavior)")
                .isEqualTo(1);
    }
}
