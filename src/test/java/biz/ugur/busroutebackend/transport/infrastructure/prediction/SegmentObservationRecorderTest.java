package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.prediction.core.RouteLine;
import biz.ugur.busroutebackend.prediction.shadow.V31Fix;
import biz.ugur.busroutebackend.prediction.shadow.V31ShadowService;
import biz.ugur.busroutebackend.transport.domain.repository.SegmentLiveStateRepository;
import biz.ugur.busroutebackend.transport.domain.repository.SegmentTravelStatsRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.SegmentTravelStat;
import biz.ugur.busroutebackend.transport.infrastructure.config.EtaLiveFactorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SegmentObservationRecorderTest {

    @Mock
    private V31ShadowService shadowService;
    @Mock
    private SegmentTravelStatsRepository historyRepository;
    @Mock
    private SegmentLiveStateRepository liveRepository;
    @Mock
    private RouteLine geom;

    private EtaLiveFactorProperties properties;
    private SegmentObservationRecorder recorder;

    private static final Instant T0 = Instant.parse("2026-07-18T08:00:00Z");

    @BeforeEach
    void setUp() {
        properties = new EtaLiveFactorProperties();
        properties.setExcludedAxes(List.of("142:0"));
        org.springframework.beans.factory.ObjectProvider<V31ShadowService> provider =
                new org.springframework.beans.factory.ObjectProvider<>() {
                    @Override
                    public V31ShadowService getObject() {
                        return shadowService;
                    }

                    @Override
                    public V31ShadowService getIfAvailable() {
                        return shadowService;
                    }

                    @Override
                    public V31ShadowService getIfUnique() {
                        return shadowService;
                    }

                    @Override
                    public V31ShadowService getObject(Object... args) {
                        return shadowService;
                    }
                };
        recorder = new SegmentObservationRecorder(
                provider, historyRepository, liveRepository, properties);
        when(historyRepository.findByKey(anyString(), anyInt(), anyString(), anyString(),
                anyInt(), anyBoolean())).thenReturn(Mono.empty());
        when(historyRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(liveRepository.recordTravel(anyString(), anyString(), anyDouble(), any()))
                .thenReturn(Mono.empty());
        when(geom.stops()).thenReturn(List.of(
                new RouteLine.StopPoint("A", 1, 1000.0),
                new RouteLine.StopPoint("B", 2, 2000.0),
                new RouteLine.StopPoint("C", 3, 3000.0)));
    }

    private static V31Fix fix(String route, long plusSec) {
        return new V31Fix("veh-1", "1111 AGJ", route, 37.95, 58.38, 30.0, 90.0,
                true, T0.plusSeconds(plusSec), 0, null, null, null);
    }

    private void tick(String route, long plusSec, double s) {
        recorder.onTick(fix(route, plusSec), geom, s, 0, 1, List.of());
    }

    @Test
    void crossingDepartAndArriveEdgesRecordsInterpolatedTravel() {
        tick("57", 0, 900.0);
        tick("57", 20, 1100.0);
        tick("57", 80, 1700.0);
        tick("57", 120, 2100.0);

        ArgumentCaptor<Double> seconds = ArgumentCaptor.forClass(Double.class);
        verify(liveRepository, timeout(2000)).recordTravel(
                org.mockito.ArgumentMatchers.eq("A"), org.mockito.ArgumentMatchers.eq("B"),
                seconds.capture(), any());
        assertThat(seconds.getValue()).isCloseTo(96.9, org.assertj.core.data.Offset.offset(1.0));
        ArgumentCaptor<SegmentTravelStat> saved = ArgumentCaptor.forClass(SegmentTravelStat.class);
        verify(historyRepository, timeout(2000)).save(saved.capture());
        assertThat(saved.getValue().getRouteNumber()).isEqualTo("57");
        assertThat(saved.getValue().getSampleCount()).isEqualTo(1);
    }

    @Test
    void multipleStopsCrossedInOneTickAllInterpolated() {
        tick("57", 0, 900.0);
        tick("57", 30, 1100.0);
        tick("57", 60, 3100.0);

        verify(liveRepository, timeout(2000)).recordTravel(
                org.mockito.ArgumentMatchers.eq("A"), org.mockito.ArgumentMatchers.eq("B"),
                anyDouble(), any());
        verify(liveRepository, timeout(2000)).recordTravel(
                org.mockito.ArgumentMatchers.eq("B"), org.mockito.ArgumentMatchers.eq("C"),
                anyDouble(), any());
    }

    @Test
    void directionChangeDropsPendingDeparture() {
        tick("57", 0, 900.0);
        tick("57", 20, 1100.0);
        recorder.onTick(fix("57", 60), geom, 2100.0, 1, 2, List.of());

        verify(liveRepository, never()).recordTravel(anyString(), anyString(), anyDouble(), any());
    }

    @Test
    void backwardJumpResetsTracking() {
        tick("57", 0, 900.0);
        tick("57", 20, 1100.0);
        tick("57", 40, 500.0);
        tick("57", 100, 2100.0);

        verify(liveRepository, never()).recordTravel(anyString(), anyString(), anyDouble(), any());
    }

    @Test
    void smallGpsJitterBackwardsDoesNotResetPendingDeparture() {
        tick("57", 0, 900.0);
        tick("57", 20, 1100.0);
        tick("57", 40, 1095.0);
        tick("57", 100, 2100.0);

        verify(liveRepository, timeout(2000)).recordTravel(
                org.mockito.ArgumentMatchers.eq("A"), org.mockito.ArgumentMatchers.eq("B"),
                anyDouble(), any());
    }

    @Test
    void excludedAxisIsIgnored() {
        properties.setExcludedAxes(List.of("57:0"));
        tick("57", 0, 900.0);
        tick("57", 20, 1100.0);
        tick("57", 120, 2100.0);

        verify(liveRepository, never()).recordTravel(anyString(), anyString(), anyDouble(), any());
    }

    @Test
    void tooFastCrossingIsDroppedAsOutOfRange() {
        tick("57", 0, 900.0);
        tick("57", 5, 1100.0);
        tick("57", 9, 2100.0);

        verify(liveRepository, never()).recordTravel(anyString(), anyString(), anyDouble(), any());
    }

    @Test
    void repositoryFailureDoesNotPropagate() {
        org.mockito.Mockito.doReturn(Mono.error(new RuntimeException("db down")))
                .when(historyRepository).save(any());
        tick("57", 0, 900.0);
        tick("57", 20, 1100.0);
        tick("57", 120, 2100.0);

        verify(historyRepository, timeout(2000)).save(any());
    }

    @Test
    void missingV31BeanDoesNotFailStartup() {
        org.springframework.beans.factory.ObjectProvider<V31ShadowService> absent =
                new org.springframework.beans.factory.ObjectProvider<>() {
                    @Override
                    public V31ShadowService getObject() {
                        throw new IllegalStateException("no bean");
                    }

                    @Override
                    public V31ShadowService getIfAvailable() {
                        return null;
                    }

                    @Override
                    public V31ShadowService getIfUnique() {
                        return null;
                    }

                    @Override
                    public V31ShadowService getObject(Object... args) {
                        throw new IllegalStateException("no bean");
                    }
                };
        SegmentObservationRecorder detached = new SegmentObservationRecorder(
                absent, historyRepository, liveRepository, properties);
        org.assertj.core.api.Assertions.assertThatCode(detached::register)
                .doesNotThrowAnyException();
    }

    @Test
    void writeDisabledFlagStopsEverything() {
        properties.setWriteEnabled(false);
        tick("57", 0, 900.0);
        tick("57", 20, 1100.0);
        tick("57", 120, 2100.0);

        verify(liveRepository, never()).recordTravel(anyString(), anyString(), anyDouble(), any());
    }
}
