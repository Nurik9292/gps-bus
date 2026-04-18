package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.routing.infrastructure.config.ETAProperties;
import biz.ugur.busroutebackend.shared.test.integration.GpsPipelineReplayRunner;
import biz.ugur.busroutebackend.shared.test.integration.PredictionSnapshot;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class GpsPipelineReplayTest {

    @Mock
    private PredictionBroadcaster broadcaster;

    @Mock
    private RouteGeometryCache routeGeometryCache;

    @Mock
    private MapMatchingService mapMatchingService;

    @Mock
    private VehiclePredictionStateRepository stateRepository;

    @Mock
    private ETAProperties etaProperties;

    @Mock
    private StopDwellStatsRepository dwellStatsRepository;

    private VehiclePositionPredictionService service;

    @BeforeEach
    void setUp() {
        PredictionProperties properties = new PredictionProperties();

        lenient().when(stateRepository.loadAll()).thenReturn(Flux.empty());
        lenient().when(stateRepository.save(any())).thenReturn(Mono.empty());
        lenient().when(stateRepository.delete(anyString())).thenReturn(Mono.empty());
        lenient().when(dwellStatsRepository.findAll()).thenReturn(Flux.empty());
        lenient().when(dwellStatsRepository.save(any())).thenReturn(Mono.empty());
        lenient().when(broadcaster.broadcast(any())).thenReturn(Mono.empty());
        lenient().when(broadcaster.getLastBroadcastPosition(anyString())).thenReturn(null);

        lenient().when(routeGeometryCache.getPoints(anyString(), anyInt())).thenReturn(null);
        lenient().when(routeGeometryCache.getTotalDistance(anyString(), anyInt())).thenReturn(0.0);
        lenient().when(routeGeometryCache.getStopFractions(anyString(), anyInt())).thenReturn(new double[0]);
        lenient().when(routeGeometryCache.getStopsAhead(anyString(), anyInt(), anyDouble())).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        ObjectProvider<GpsRecorder> gpsRecorderProvider = mock(ObjectProvider.class);
        lenient().when(gpsRecorderProvider.getIfAvailable()).thenReturn(null);

        service = new VehiclePositionPredictionService(
                properties, broadcaster, routeGeometryCache, mapMatchingService,
                stateRepository, dwellStatsRepository, gpsRecorderProvider,
                new GpsOutlierFilter()
        );
    }

    @Test
    void syntheticSmokeFixtureProducesDeterministicSnapshots() throws Exception {
        GpsPipelineReplayRunner runner = new GpsPipelineReplayRunner(service, service::snapshotAllStatesForTest);

        List<List<PredictionSnapshot>> snapshots = runner.replay("synthetic-smoke");

        assertThat(snapshots).hasSize(3);
        assertThat(snapshots.get(2))
                .anySatisfy(snap -> assertThat(snap.vehicleId()).isEqualTo("test-vehicle-1"));
    }

    @Test
    void happyPathSingleBusProducesDeterministicSnapshots() throws Exception {
        GpsPipelineReplayRunner runner = new GpsPipelineReplayRunner(service, service::snapshotAllStatesForTest);

        List<List<PredictionSnapshot>> snapshots = runner.replay("happy-path-single-bus");

        assertThat(snapshots).hasSize(25);
        assertThat(snapshots.get(snapshots.size() - 1))
                .anySatisfy(snap -> {
                    assertThat(snap.licensePlate()).isEqualTo("1988 AGH");
                    assertThat(snap.routeNumber()).isEqualTo("80");
                });
    }
}
