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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class VehiclePositionPredictionServiceTest {

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
        lenient().when(dwellStatsRepository.findAll()).thenReturn(Flux.empty());
        @SuppressWarnings("unchecked")
        ObjectProvider<GpsRecorder> gpsRecorderProvider = mock(ObjectProvider.class);
        lenient().when(gpsRecorderProvider.getIfAvailable()).thenReturn(null);
        service = new VehiclePositionPredictionService(
                properties, broadcaster, routeGeometryCache, mapMatchingService,
                stateRepository, dwellStatsRepository, gpsRecorderProvider
        );
    }

    @Test
    void emptyServiceReturnsZeroActiveStates() {
        assertThat(service.getActiveStateCount()).isZero();
        assertThat(service.getActiveStates()).isEmpty();
    }

    @Test
    void unknownVehicleHasStaleConfidence() {
        assertThat(service.getConfidence("vehicle-xyz")).isEqualTo(PositionConfidence.STALE);
    }

    @Test
    void hasActiveStateReturnsFalseForUnknownVehicle() {
        assertThat(service.hasActiveState("vehicle-xyz")).isFalse();
    }

    @Test
    void isActivelyPredictingReturnsFalseForUnknownVehicle() {
        assertThat(service.isActivelyPredicting("vehicle-xyz")).isFalse();
    }

    @Test
    void hasPredictionStateReturnsFalseForUnknownVehicle() {
        assertThat(service.hasPredictionState("vehicle-xyz")).isFalse();
    }

    @Test
    void drainPendingDirectionFixesReturnsEmptyWhenNothingPending() {
        assertThat(service.drainPendingDirectionFixes()).isEmpty();
    }

    @Test
    void disabledServiceReturnsEmptyActiveStates() {
        properties.setEnabled(false);
        assertThat(service.getActiveStates()).isEmpty();
        assertThat(service.hasActiveState("any")).isFalse();
        assertThat(service.isActivelyPredicting("any")).isFalse();
        assertThat(service.hasPredictionState("any")).isFalse();
    }
}
