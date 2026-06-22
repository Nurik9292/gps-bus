package biz.ugur.busroutebackend.transport.application.usecase.pipeline;

import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.service.GpsOutlierDetector;
import biz.ugur.busroutebackend.transport.domain.valueobject.OutlierDetectionResult;
import biz.ugur.busroutebackend.transport.infrastructure.config.GpsOutlierDetectionProperties;
import biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer;
import biz.ugur.busroutebackend.transport.infrastructure.metrics.GpsOutlierMetricsRecorder;
import biz.ugur.busroutebackend.transport.infrastructure.redis.VehicleGpsHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutlierFilterStageTest {

    private GpsOutlierDetector outlierDetector;
    private GpsOutlierMetricsRecorder metricsRecorder;
    private GpsOutlierDetectionProperties properties;
    private VehicleGpsHistoryService historyService;
    private VehicleRepository vehicleRepository;
    private PipelineTracer pipelineTracer;
    private OutlierFilterStage stage;

    @BeforeEach
    void setUp() {
        outlierDetector = mock(GpsOutlierDetector.class);
        metricsRecorder = mock(GpsOutlierMetricsRecorder.class);
        properties = new GpsOutlierDetectionProperties();
        properties.setEnabled(true);
        properties.setRejectOutliers(true);
        properties.setRejectFrozenMotion(true);
        properties.setHistoryPointsToCheck(3);
        historyService = mock(VehicleGpsHistoryService.class);
        vehicleRepository = mock(VehicleRepository.class);
        pipelineTracer = mock(PipelineTracer.class);

        stage = new OutlierFilterStage(outlierDetector, metricsRecorder, properties,
                historyService, vehicleRepository, pipelineTracer);

        lenient().when(historyService.getHistoryBatch(anyList(), anyInt()))
                .thenReturn(Mono.just(Map.of()));
        lenient().when(vehicleRepository.findByDeviceIds(anyList()))
                .thenReturn(Mono.just(Map.of()));
    }

    private GpsPositionDTO position(String deviceId) {
        GpsPositionDTO p = new GpsPositionDTO();
        p.setDeviceId(deviceId);
        p.setLatitude(37.95);
        p.setLongitude(58.35);
        p.setFixTime(LocalDateTime.now());
        p.setSpeed(15.0);
        return p;
    }

    @Test
    void disabledShortCircuitsAndReturnsInputUnchanged() {
        properties.setEnabled(false);
        List<GpsPositionDTO> input = List.of(position("dev-1"), position("dev-2"));

        StepVerifier.create(stage.apply(input))
                .assertNext(result -> {
                    org.assertj.core.api.Assertions.assertThat(result).hasSize(2);
                })
                .verifyComplete();
    }

    @Test
    void validPositionsArePassedThrough() {
        when(outlierDetector.detectWithHistory(anyString(), anyDouble(), anyDouble(), any(), anyList(), any()))
                .thenReturn(OutlierDetectionResult.valid("dev-1", 30.0, 100.0, 10, 150.0));

        List<GpsPositionDTO> input = List.of(position("dev-1"));

        StepVerifier.create(stage.apply(input))
                .assertNext(result -> {
                    org.assertj.core.api.Assertions.assertThat(result).hasSize(1);
                    org.assertj.core.api.Assertions.assertThat(result.get(0).getDeviceId()).isEqualTo("dev-1");
                })
                .verifyComplete();

        verify(metricsRecorder).recordBatch(anyList());
    }

    @Test
    void teleportationOutliersAreRejectedWhenRejectOutliersEnabled() {
        when(outlierDetector.detectWithHistory(anyString(), anyDouble(), anyDouble(), any(), anyList(), any()))
                .thenReturn(OutlierDetectionResult.outlier("dev-1", 500.0, 5000.0, 5, 150.0));

        List<GpsPositionDTO> input = List.of(position("dev-1"));

        StepVerifier.create(stage.apply(input))
                .assertNext(result -> org.assertj.core.api.Assertions.assertThat(result).isEmpty())
                .verifyComplete();
    }

    @Test
    void teleportationOutliersAreKeptWhenRejectOutliersDisabled() {
        properties.setRejectOutliers(false);
        when(outlierDetector.detectWithHistory(anyString(), anyDouble(), anyDouble(), any(), anyList(), any()))
                .thenReturn(OutlierDetectionResult.outlier("dev-1", 500.0, 5000.0, 5, 150.0));

        List<GpsPositionDTO> input = List.of(position("dev-1"));

        StepVerifier.create(stage.apply(input))
                .assertNext(result -> org.assertj.core.api.Assertions.assertThat(result).hasSize(1))
                .verifyComplete();
    }

    @Test
    void frozenCoordinatesAreRejectedWhenRejectFrozenMotionEnabled() {
        when(outlierDetector.detectWithHistory(anyString(), anyDouble(), anyDouble(), any(), anyList(), any()))
                .thenReturn(OutlierDetectionResult.frozenCoordinatesWithMotion("dev-1", 1.0, 30.0));

        List<GpsPositionDTO> input = List.of(position("dev-1"));

        StepVerifier.create(stage.apply(input))
                .assertNext(result -> org.assertj.core.api.Assertions.assertThat(result).isEmpty())
                .verifyComplete();
    }

    @Test
    void frozenCoordinatesAreKeptWhenRejectFrozenMotionDisabled() {
        properties.setRejectFrozenMotion(false);
        when(outlierDetector.detectWithHistory(anyString(), anyDouble(), anyDouble(), any(), anyList(), any()))
                .thenReturn(OutlierDetectionResult.frozenCoordinatesWithMotion("dev-1", 1.0, 30.0));

        List<GpsPositionDTO> input = List.of(position("dev-1"));

        StepVerifier.create(stage.apply(input))
                .assertNext(result -> org.assertj.core.api.Assertions.assertThat(result).hasSize(1))
                .verifyComplete();
    }

    @Test
    void noHistoryResultsAreTreatedAsValid() {
        when(outlierDetector.detectWithHistory(anyString(), anyDouble(), anyDouble(), any(), anyList(), any()))
                .thenReturn(OutlierDetectionResult.noHistory("dev-1", 150.0));

        List<GpsPositionDTO> input = List.of(position("dev-1"));

        StepVerifier.create(stage.apply(input))
                .assertNext(result -> org.assertj.core.api.Assertions.assertThat(result).hasSize(1))
                .verifyComplete();
    }

    @Test
    void mixedOutliersAndValidsCountedCorrectly() {
        when(outlierDetector.detectWithHistory(eq("dev-1"), anyDouble(), anyDouble(), any(), anyList(), any()))
                .thenReturn(OutlierDetectionResult.valid("dev-1", 30.0, 100.0, 10, 150.0));
        when(outlierDetector.detectWithHistory(eq("dev-2"), anyDouble(), anyDouble(), any(), anyList(), any()))
                .thenReturn(OutlierDetectionResult.outlier("dev-2", 500.0, 5000.0, 5, 150.0));
        when(outlierDetector.detectWithHistory(eq("dev-3"), anyDouble(), anyDouble(), any(), anyList(), any()))
                .thenReturn(OutlierDetectionResult.frozenCoordinatesWithMotion("dev-3", 1.0, 30.0));

        List<GpsPositionDTO> input = List.of(position("dev-1"), position("dev-2"), position("dev-3"));

        StepVerifier.create(stage.apply(input))
                .assertNext(result -> {
                    org.assertj.core.api.Assertions.assertThat(result).hasSize(1);
                    org.assertj.core.api.Assertions.assertThat(result.get(0).getDeviceId()).isEqualTo("dev-1");
                })
                .verifyComplete();
    }

    @Test
    void emptyHistoryUsesDbPositionAsBaseline() {
        Vehicle v = Vehicle.builder()
                .deviceId("dev-1")
                .currentLatitude(37.90)
                .currentLongitude(58.30)
                .lastPositionUpdate(LocalDateTime.now().minusMinutes(40))
                .build();
        lenient().when(vehicleRepository.findByDeviceIds(anyList()))
                .thenReturn(Mono.just(Map.of("dev-1", v)));
        when(outlierDetector.detectWithHistory(anyString(), anyDouble(), anyDouble(), any(), anyList(), any()))
                .thenReturn(OutlierDetectionResult.valid("dev-1", 30.0, 100.0, 10, 150.0));

        stage.apply(List.of(position("dev-1"))).block();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<? extends GpsOutlierDetector.GpsHistoryPoint>> historyCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(outlierDetector).detectWithHistory(eq("dev-1"), anyDouble(), anyDouble(), any(),
                historyCaptor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(historyCaptor.getValue()).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(historyCaptor.getValue().get(0).getLatitude())
                .isEqualTo(37.90);
    }

    @Test
    void emptyInputProducesEmptyOutput() {
        StepVerifier.create(stage.apply(List.of()))
                .assertNext(result -> org.assertj.core.api.Assertions.assertThat(result).isEmpty())
                .verifyComplete();
    }
}
