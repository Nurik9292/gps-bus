package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.routing.infrastructure.config.ETAProperties;
import biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.DirectVehiclePositionBroadcaster;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PredictionBroadcasterGlideTest {

    @Mock
    private DirectVehiclePositionBroadcaster directBroadcaster;

    @Mock
    private RouteGeometryCache routeGeometryCache;

    private final ETAProperties etaProperties = new ETAProperties();
    private final PredictionProperties properties = new PredictionProperties();

    private PredictionBroadcaster broadcaster;

    private static final double START_LAT = 37.90250;
    private static final double START_LON = 58.34425;
    private static final double FAR_LAT = 37.91150;
    private static final double FAR_LON = 58.34425;

    @BeforeEach
    void setUp() {
        broadcaster = new PredictionBroadcaster(
                directBroadcaster, routeGeometryCache, etaProperties,
                properties, org.mockito.Mockito.mock(VehiclePositionPredictor.class),
                new PipelineTracer());
        lenient().when(routeGeometryCache.getStopsAhead(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of());
    }

    private static VehiclePredictionState.VehiclePredictionStateBuilder baseline() {
        return VehiclePredictionState.builder()
                .vehicleId("veh-1")
                .licensePlate("1679 AGJ")
                .routeNumber("58")
                .direction(0)
                .inMotion(true)
                .speedKmh(20.0)
                .rawGpsSpeedKmh(20.0)
                .predictedLatitude(START_LAT)
                .predictedLongitude(START_LON)
                .routeCoordinates(List.of(
                        new double[]{START_LAT, START_LON},
                        new double[]{FAR_LAT, FAR_LON}))
                .totalRouteDistanceMeters(10_000)
                .fractionOnRoute(0.5)
                .course(0.0)
                .lastReceivedAt(Instant.now());
    }

    private static double dist(double lat1, double lon1, double lat2, double lon2) {
        return DistanceCalculationService.haversineDistanceMeters(lat1, lon1, lat2, lon2);
    }

    @Test
    void farJumpIsGlidedNotTeleportedNotFrozen() {
        double maxStep = properties.getMaxBroadcastStepMeters();

        StepVerifier.create(broadcaster.broadcast(baseline().build())).verifyComplete();

        VehiclePredictionState jumped = baseline()
                .predictedLatitude(FAR_LAT)
                .predictedLongitude(FAR_LON)
                .build();
        StepVerifier.create(broadcaster.broadcast(jumped)).verifyComplete();

        VehiclePositionWebSocketMessage msg = captureNth(1);
        double movedFromStart = dist(START_LAT, START_LON, msg.getLatitude(), msg.getLongitude());
        double remainingToTarget = dist(FAR_LAT, FAR_LON, msg.getLatitude(), msg.getLongitude());

        assertThat(movedFromStart).as("not frozen — must move toward target").isGreaterThan(1.0);
        assertThat(movedFromStart).as("glided — bounded by max step").isLessThanOrEqualTo(maxStep + 2.0);
        assertThat(remainingToTarget).as("not teleported — far short of the full jump").isGreaterThan(maxStep);
    }

    @Test
    void glideKeepsProgressingTowardTargetAcrossTicks() {
        StepVerifier.create(broadcaster.broadcast(baseline().build())).verifyComplete();
        StepVerifier.create(broadcaster.broadcast(baseline().predictedLatitude(FAR_LAT).predictedLongitude(FAR_LON).build())).verifyComplete();
        StepVerifier.create(broadcaster.broadcast(baseline().predictedLatitude(FAR_LAT).predictedLongitude(FAR_LON).build())).verifyComplete();

        VehiclePositionWebSocketMessage tick1 = captureNth(1);
        VehiclePositionWebSocketMessage tick2 = captureNth(2);

        double t1FromStart = dist(START_LAT, START_LON, tick1.getLatitude(), tick1.getLongitude());
        double t2FromStart = dist(START_LAT, START_LON, tick2.getLatitude(), tick2.getLongitude());

        assertThat(t2FromStart).as("each tick advances further toward target — no stall").isGreaterThan(t1FromStart);
    }

    private VehiclePositionWebSocketMessage captureNth(int index) {
        ArgumentCaptor<VehiclePositionWebSocketMessage> captor =
                ArgumentCaptor.forClass(VehiclePositionWebSocketMessage.class);
        verify(directBroadcaster, org.mockito.Mockito.atLeast(index + 1)).broadcastDirect(captor.capture());
        return captor.getAllValues().get(index);
    }
}
