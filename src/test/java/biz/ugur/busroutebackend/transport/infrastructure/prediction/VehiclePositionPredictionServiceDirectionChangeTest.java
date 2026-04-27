package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.transport.domain.repository.StopDwellStatsRepository;
import biz.ugur.busroutebackend.transport.infrastructure.debug.GpsRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehiclePositionPredictionServiceDirectionChangeTest {

    private static final String VEHICLE_ID = "veh-1";
    private static final String PLATE = "6368 AGJ";
    private static final String ROUTE = "160";
    private static final double LAT = 37.90270;
    private static final double LON = 58.34309;

    @Mock
    private PredictionBroadcaster broadcaster;

    @Mock
    private RouteGeometryCache routeGeometryCache;

    @Mock
    private MapMatchingService mapMatchingService;

    @Mock
    private VehiclePredictionStateRepository stateRepository;

    @Mock
    private StopDwellStatsRepository dwellStatsRepository;

    private PredictionProperties properties;
    private VehiclePositionPredictionService service;

    @BeforeEach
    void setUp() {
        properties = new PredictionProperties();
        lenient().when(stateRepository.loadAll()).thenReturn(Flux.empty());
        lenient().when(stateRepository.save(any())).thenReturn(Mono.empty());
        lenient().when(dwellStatsRepository.findAll()).thenReturn(Flux.empty());

        List<double[]> coords = List.of(
                new double[]{LAT, LON},
                new double[]{LAT + 0.001, LON + 0.001});
        lenient().when(routeGeometryCache.getPoints(eq(ROUTE), anyInt())).thenReturn(coords);
        lenient().when(routeGeometryCache.getTotalDistance(eq(ROUTE), anyInt())).thenReturn(1_000.0);
        lenient().when(routeGeometryCache.getCumulativeDistances(eq(ROUTE), anyInt())).thenReturn(null);

        MapMatchingService.SnappedResult snap =
                new MapMatchingService.SnappedResult(LAT, LON, 0.5, 20.0, true);
        lenient().when(mapMatchingService.snapToNearestSegment(anyDouble(), anyDouble(), any(), anyDouble()))
                .thenReturn(snap);
        lenient().when(mapMatchingService.snapToNearestSegment(anyDouble(), anyDouble(), any(), any(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(snap);
        lenient().when(mapMatchingService.calculateCourseFromRoute(any(), any(), anyDouble(), anyInt(), anyDouble()))
                .thenReturn(90.0);

        @SuppressWarnings("unchecked")
        ObjectProvider<GpsRecorder> gpsRecorderProvider = mock(ObjectProvider.class);
        lenient().when(gpsRecorderProvider.getIfAvailable()).thenReturn(null);

        service = new VehiclePositionPredictionService(
                properties, broadcaster, routeGeometryCache,
                stateRepository, gpsRecorderProvider,
                new GpsOutlierFilter(new PredictionProperties()),
                new SnapCorrector(properties, routeGeometryCache, mapMatchingService, new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.DirectionChangeCooldown(properties), new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.PlausibilityChecker(properties), new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.ConsecutiveOppositeCounter()),
                new VehiclePositionPredictor(properties, routeGeometryCache, mapMatchingService, dwellStatsRepository)
        );
    }

    @Test
    void detectsExternalDirectionChange_resetsLastGpsFraction_setsCooldown() {
        Instant earlyBoundary = Instant.now().minusSeconds(2);

        service.onGpsUpdate(VEHICLE_ID, PLATE, ROUTE,
                LAT, LON, 30.0, 90.0, true,
                Instant.now().minusSeconds(1), 0, false, false);
        service.onGpsUpdate(VEHICLE_ID, PLATE, ROUTE,
                LAT + 0.0001, LON + 0.0001, 30.0, 90.0, true,
                Instant.now(), 1, false, false);

        VehiclePredictionState state = service.snapshotAllStatesForTest().stream()
                .filter(s -> VEHICLE_ID.equals(s.getVehicleId()))
                .findFirst()
                .orElseThrow();

        assertThat(state.getDirection())
                .as("state.direction must be updated to the new external direction")
                .isEqualTo(1);
        assertThat(state.getDirectionChangedAt())
                .as("directionChangedAt must be set when external direction changes")
                .isNotNull();
        assertThat(state.getDirectionChangedAt())
                .as("directionChangedAt must be after the boundary timestamp")
                .isAfter(earlyBoundary);
    }

    @Test
    void directionExternalChange_resetsAllDirectionTiedFields() {
        service.onGpsUpdate(VEHICLE_ID, PLATE, ROUTE,
                LAT, LON, 30.0, 90.0, true,
                Instant.now().minusSeconds(1), 0, false, false);

        VehiclePredictionState before = service.snapshotAllStatesForTest().stream()
                .filter(s -> VEHICLE_ID.equals(s.getVehicleId()))
                .findFirst()
                .orElseThrow();
        VehiclePredictionState polluted = before.toBuilder()
                .fractionOnRoute(0.5386)
                .lastGpsFraction(0.5386)
                .lastRejectedGpsFraction(0.51)
                .consecutiveImplausibleCount(2)
                .consecutiveInconsistentAdvanceCount(1)
                .consecutiveOffRouteCount(3)
                .offRoute(true)
                .dwellStartedAt(Instant.now())
                .dwellStopFraction(0.4)
                .dwellStopId("stop-X")
                .build();
        service.replaceStateForTest(VEHICLE_ID, polluted);

        double newLat = LAT + 0.0001;
        double newLon = LON + 0.0001;
        service.onGpsUpdate(VEHICLE_ID, PLATE, ROUTE,
                newLat, newLon, 30.0, 90.0, true,
                Instant.now(), 1, false, false);

        VehiclePredictionState state = service.snapshotAllStatesForTest().stream()
                .filter(s -> VEHICLE_ID.equals(s.getVehicleId()))
                .findFirst()
                .orElseThrow();

        assertThat(state.getDirection()).isEqualTo(1);
        assertThat(state.getFractionOnRoute())
                .as("polluted forward frac 0.5386 must NOT survive direction change")
                .isNotEqualTo(0.5386);
        assertThat(state.getLastGpsFraction())
                .as("polluted lastGpsFraction must NOT survive direction change")
                .isNotEqualTo(0.5386);
        assertThat(state.getLastRejectedGpsFraction())
                .as("polluted lastRejectedGpsFraction must NOT survive direction change")
                .isNotEqualTo(0.51);
        assertThat(state.getConsecutiveInconsistentAdvanceCount())
                .as("inconsistent-advance counter must reset on direction change")
                .isEqualTo(0);
        assertThat(state.getConsecutiveOffRouteCount())
                .as("off-route counter must reset on direction change")
                .isEqualTo(0);
        assertThat(state.isOffRoute())
                .as("off-route flag must reset on direction change")
                .isFalse();
        assertThat(state.getDwellStartedAt())
                .as("dwell tracking must reset on direction change")
                .isNull();
        assertThat(state.getDwellStopFraction()).isEqualTo(-1);
        assertThat(state.getDwellStopId()).isNull();
        assertThat(state.getDirectionChangedAt()).isNotNull();
    }

    @Test
    void directionExternalChange_preservesDirectionIndependentFields() {
        service.onGpsUpdate(VEHICLE_ID, PLATE, ROUTE,
                LAT, LON, 30.0, 90.0, true,
                Instant.now().minusSeconds(1), 0, false, false);

        service.onGpsUpdate(VEHICLE_ID, PLATE, ROUTE,
                LAT + 0.0001, LON + 0.0001, 30.0, 90.0, true,
                Instant.now(), 1, false, false);

        VehiclePredictionState state = service.snapshotAllStatesForTest().stream()
                .filter(s -> VEHICLE_ID.equals(s.getVehicleId()))
                .findFirst()
                .orElseThrow();

        assertThat(state.getRouteNumber())
                .as("route assignment must be preserved across direction change")
                .isEqualTo(ROUTE);
        assertThat(state.getKalmanSpeedKmh())
                .as("Kalman speed estimate must not be reset by direction change")
                .isGreaterThanOrEqualTo(0.0);
        assertThat(state.getKalmanSpeedVariance())
                .as("Kalman variance must not be reset by direction change")
                .isLessThan(1000.0);
    }

    @Test
    void doesNotResetWhenDirectionUnchanged() {
        service.onGpsUpdate(VEHICLE_ID, PLATE, ROUTE,
                LAT, LON, 30.0, 90.0, true,
                Instant.now().minusSeconds(2), 0, false, false);
        service.onGpsUpdate(VEHICLE_ID, PLATE, ROUTE,
                LAT + 0.0001, LON + 0.0001, 30.0, 90.0, true,
                Instant.now().minusSeconds(1), 0, false, false);
        service.onGpsUpdate(VEHICLE_ID, PLATE, ROUTE,
                LAT + 0.0002, LON + 0.0002, 30.0, 90.0, true,
                Instant.now(), 0, false, false);

        VehiclePredictionState state = service.snapshotAllStatesForTest().stream()
                .filter(s -> VEHICLE_ID.equals(s.getVehicleId()))
                .findFirst()
                .orElseThrow();

        assertThat(state.getDirection()).isEqualTo(0);
        assertThat(state.getDirectionChangedAt())
                .as("directionChangedAt stays null when direction has not changed")
                .isNull();
    }
}
