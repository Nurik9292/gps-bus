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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OffRouteDetectionRawSnapDistanceTest {

    private static final String VEHICLE_ID = "veh-1";
    private static final String PLATE = "6292 AGJ";
    private static final String ROUTE = "160";
    private static final double LAT = 37.9048;
    private static final double LON = 58.3365;

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

    private void mockSnapDistance(double distMeters) {
        boolean snapped = distMeters <= properties.getMaxSnapDistanceMeters();
        MapMatchingService.SnappedResult snap =
                new MapMatchingService.SnappedResult(LAT, LON, snapped ? 0.5 : -1, distMeters, snapped);
        lenient().when(mapMatchingService.snapToNearestSegment(anyDouble(), anyDouble(), any(), anyDouble()))
                .thenReturn(snap);
        lenient().when(mapMatchingService.snapToNearestSegment(anyDouble(), anyDouble(), any(), any(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(snap);
        lenient().when(mapMatchingService.calculateCourseFromRoute(any(), any(), anyDouble(), anyInt(), anyDouble()))
                .thenReturn(90.0);
    }

    @Test
    void notOffRoute_whenSnapMinDistanceWithinThreshold_evenIfPredictedHeldFar() {
        mockSnapDistance(4.0);

        for (int i = 0; i < 10; i++) {
            service.onGpsUpdate(VEHICLE_ID, PLATE, ROUTE,
                    LAT + i * 0.00001, LON + i * 0.00001,
                    45.0, 90.0, true,
                    Instant.now().minusSeconds(10 - i), 0, false, false);
        }

        VehiclePredictionState state = service.snapshotAllStatesForTest().stream()
                .filter(s -> VEHICLE_ID.equals(s.getVehicleId()))
                .findFirst()
                .orElseThrow();

        assertThat(state.getConsecutiveOffRouteCount())
                .as("4m snap distance < 200m threshold — must NOT count as off-route, " +
                    "even across many GPS updates")
                .isEqualTo(0);
        assertThat(state.isOffRoute()).isFalse();
    }

    @Test
    void offRouteTriggersAfterFiveConfirmationsWhenSnapMinDistanceTooFar() {
        mockSnapDistance(500.0);

        for (int i = 0; i < 5; i++) {
            service.onGpsUpdate(VEHICLE_ID, PLATE, ROUTE,
                    LAT + i * 0.00001, LON + i * 0.00001,
                    45.0, 90.0, true,
                    Instant.now().minusSeconds(5 - i), 0, false, false);
        }

        VehiclePredictionState state = service.snapshotAllStatesForTest().stream()
                .filter(s -> VEHICLE_ID.equals(s.getVehicleId()))
                .findFirst()
                .orElseThrow();

        assertThat(state.getConsecutiveOffRouteCount())
                .as("5 consecutive updates with snap dist > 200m threshold")
                .isGreaterThanOrEqualTo(5);
        assertThat(state.isOffRoute())
                .as("off-route flag must be set after >= OFF_ROUTE_CONFIRMATIONS")
                .isTrue();
    }

    @Test
    void offRouteClearsOnceVehicleReturnsCloseToPolyline() {
        mockSnapDistance(500.0);
        for (int i = 0; i < 6; i++) {
            service.onGpsUpdate(VEHICLE_ID, PLATE, ROUTE,
                    LAT, LON, 45.0, 90.0, true,
                    Instant.now().minusSeconds(10 - i), 0, false, false);
        }

        mockSnapDistance(50.0);
        service.onGpsUpdate(VEHICLE_ID, PLATE, ROUTE,
                LAT, LON, 45.0, 90.0, true,
                Instant.now(), 0, false, false);

        VehiclePredictionState state = service.snapshotAllStatesForTest().stream()
                .filter(s -> VEHICLE_ID.equals(s.getVehicleId()))
                .findFirst()
                .orElseThrow();

        assertThat(state.getConsecutiveOffRouteCount()).isEqualTo(0);
        assertThat(state.isOffRoute())
                .as("returning within threshold clears off-route flag")
                .isFalse();
    }

    @Test
    void noOffRouteCountWhenSnapNotAttempted_routeMissing() {
        lenient().when(routeGeometryCache.getPoints(eq("999"), anyInt())).thenReturn(null);
        lenient().when(routeGeometryCache.getTotalDistance(eq("999"), anyInt())).thenReturn(0.0);

        for (int i = 0; i < 10; i++) {
            service.onGpsUpdate(VEHICLE_ID, PLATE, "999",
                    LAT, LON, 45.0, 90.0, true,
                    Instant.now().minusSeconds(10 - i), 0, false, false);
        }

        VehiclePredictionState state = service.snapshotAllStatesForTest().stream()
                .filter(s -> VEHICLE_ID.equals(s.getVehicleId()))
                .findFirst()
                .orElseThrow();

        assertThat(state.getConsecutiveOffRouteCount())
                .as("when no route geometry exists snap is not attempted; off-route counter must NOT grow")
                .isEqualTo(0);
        assertThat(state.isOffRoute()).isFalse();
    }
}
