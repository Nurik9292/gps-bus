package biz.ugur.busroutebackend.transport.application.usecase.pipeline;

import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.service.VehicleValidationService;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsValidationResult;
import biz.ugur.busroutebackend.transport.infrastructure.metrics.GpsValidationMetricsRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GpsValidationStageTest {

    private VehicleValidationService validationService;
    private GpsValidationMetricsRecorder metricsRecorder;
    private GpsValidationStage stage;

    @BeforeEach
    void setUp() {
        validationService = mock(VehicleValidationService.class);
        metricsRecorder = mock(GpsValidationMetricsRecorder.class);
        stage = new GpsValidationStage(validationService, metricsRecorder);
        lenient().when(validationService.validateGpsPosition(any())).thenReturn(GpsValidationResult.VALID);
    }

    private GpsPositionDTO position(String deviceId, LocalDateTime fixTime, Double hdop, Integer sat) {
        GpsPositionDTO p = new GpsPositionDTO();
        p.setDeviceId(deviceId);
        p.setFixTime(fixTime);
        p.setLatitude(37.95);
        p.setLongitude(58.35);
        p.setHdop(hdop);
        p.setSatellites(sat);
        return p;
    }

    private LocalDateTime fresh() {
        return LocalDateTime.now(ZoneOffset.UTC).minusSeconds(10);
    }

    @Test
    void emptyInputReturnsEmptyBatch() {
        GpsValidationStage.ValidatedGpsBatch result = stage.apply(List.of());

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.invalidCount()).isZero();
    }

    @Test
    void allInvalidReturnsEmptyAcceptedWithFullInvalidCount() {
        when(validationService.validateGpsPosition(any())).thenReturn(GpsValidationResult.NULL_COORDINATES);
        List<GpsPositionDTO> positions = List.of(
                position("dev-1", fresh(), 1.0, 8),
                position("dev-2", fresh(), 1.0, 8)
        );

        GpsValidationStage.ValidatedGpsBatch result = stage.apply(positions);

        assertThat(result.accepted()).isEmpty();
        assertThat(result.invalidCount()).isEqualTo(2);
    }

    @Test
    void staleFilterDropsPositionsOlderThanCutoff() {
        LocalDateTime stale = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5);
        List<GpsPositionDTO> positions = List.of(
                position("dev-1", stale, 1.0, 8),
                position("dev-2", fresh(), 1.0, 8)
        );

        GpsValidationStage.ValidatedGpsBatch result = stage.apply(positions);

        assertThat(result.accepted()).hasSize(1);
        assertThat(result.accepted().get(0).getDeviceId()).isEqualTo("dev-2");
    }

    @Test
    void qualityFilterDropsPositionsWithBadHdop() {
        List<GpsPositionDTO> positions = List.of(
                position("dev-1", fresh(), 10.0, 8),
                position("dev-2", fresh(), 1.0, 8)
        );

        GpsValidationStage.ValidatedGpsBatch result = stage.apply(positions);

        assertThat(result.accepted()).hasSize(1);
        assertThat(result.accepted().get(0).getDeviceId()).isEqualTo("dev-2");
    }

    @Test
    void qualityFilterDropsPositionsWithFewSatellites() {
        List<GpsPositionDTO> positions = List.of(
                position("dev-1", fresh(), 1.0, 2),
                position("dev-2", fresh(), 1.0, 8)
        );

        GpsValidationStage.ValidatedGpsBatch result = stage.apply(positions);

        assertThat(result.accepted()).hasSize(1);
        assertThat(result.accepted().get(0).getDeviceId()).isEqualTo("dev-2");
    }

    @Test
    void idempotencyDropsRepeatedFixTimePerDevice() {
        LocalDateTime t1 = fresh();
        LocalDateTime t2 = t1.plusSeconds(5);

        GpsValidationStage.ValidatedGpsBatch firstRun = stage.apply(List.of(
                position("dev-A", t1, 1.0, 8)
        ));
        assertThat(firstRun.accepted()).hasSize(1);

        GpsValidationStage.ValidatedGpsBatch secondRun = stage.apply(List.of(
                position("dev-A", t1, 1.0, 8),
                position("dev-A", t2, 1.0, 8)
        ));
        assertThat(secondRun.accepted()).hasSize(1);
        assertThat(secondRun.accepted().get(0).getFixTime()).isEqualTo(t2);
    }

    @Test
    void idempotencyAcceptsPositionsWithoutFixTime() {
        GpsPositionDTO noFix = position("dev-A", null, 1.0, 8);

        GpsValidationStage.ValidatedGpsBatch result = stage.apply(List.of(noFix));

        assertThat(result.accepted()).isEmpty();
    }

    @Test
    void mixedValidAndInvalidCountsCorrectly() {
        when(validationService.validateGpsPosition(any()))
                .thenReturn(GpsValidationResult.VALID,
                            GpsValidationResult.NULL_COORDINATES,
                            GpsValidationResult.VALID);

        List<GpsPositionDTO> positions = List.of(
                position("dev-1", fresh(), 1.0, 8),
                position("dev-2", fresh(), 1.0, 8),
                position("dev-3", fresh(), 1.0, 8)
        );

        GpsValidationStage.ValidatedGpsBatch result = stage.apply(positions);

        assertThat(result.accepted()).hasSize(2);
        assertThat(result.invalidCount()).isEqualTo(1);
    }

    @Test
    void allStaleReturnsEmptyAccepted() {
        LocalDateTime stale = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10);
        List<GpsPositionDTO> positions = List.of(
                position("dev-1", stale, 1.0, 8),
                position("dev-2", stale, 1.0, 8)
        );

        GpsValidationStage.ValidatedGpsBatch result = stage.apply(positions);

        assertThat(result.accepted()).isEmpty();
    }
}
