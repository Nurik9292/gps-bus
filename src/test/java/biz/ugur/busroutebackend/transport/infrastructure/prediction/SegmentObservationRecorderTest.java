package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.prediction.core.StopAware;
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

    private EtaLiveFactorProperties properties;
    private SegmentObservationRecorder recorder;

    private static final Instant T0 = Instant.parse("2026-07-18T08:00:00Z");

    @BeforeEach
    void setUp() {
        properties = new EtaLiveFactorProperties();
        properties.setExcludedAxes(List.of("142:0"));
        recorder = new SegmentObservationRecorder(
                shadowService, historyRepository, liveRepository, properties);
        when(historyRepository.findByKey(anyString(), anyInt(), anyString(), anyString(),
                anyInt(), anyBoolean())).thenReturn(Mono.empty());
        when(historyRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(liveRepository.recordTravel(anyString(), anyString(), anyDouble(), any()))
                .thenReturn(Mono.empty());
    }

    private static V31Fix fix(String route) {
        return new V31Fix("veh-1", "1111 AGJ", route, 37.95, 58.38, 30.0, 90.0,
                true, T0, 0, null, null, null);
    }

    private static StopAware.StopEvent ev(StopAware.StopEventType type, String stop, long plusSec) {
        return new StopAware.StopEvent(type, stop, T0.plusSeconds(plusSec));
    }

    @Test
    void travelBetweenConsecutiveStopsIsRecordedToHistoryAndLive() {
        recorder.accept(fix("57"), 0, 1, List.of(ev(StopAware.StopEventType.DWELL_EXIT, "A", 0)));
        recorder.accept(fix("57"), 0, 1, List.of(ev(StopAware.StopEventType.DWELL_ENTER, "B", 90)));

        verify(liveRepository, timeout(2000)).recordTravel("A", "B", 90.0, T0.plusSeconds(90));
        ArgumentCaptor<SegmentTravelStat> saved = ArgumentCaptor.forClass(SegmentTravelStat.class);
        verify(historyRepository, timeout(2000)).save(saved.capture());
        assertThat(saved.getValue().getRouteNumber()).isEqualTo("57");
        assertThat(saved.getValue().getAvgTravelSeconds()).isEqualTo(90.0);
        assertThat(saved.getValue().getSampleCount()).isEqualTo(1);
    }

    @Test
    void skipCountsAsArrivalAndNextDeparture() {
        recorder.accept(fix("57"), 0, 1, List.of(ev(StopAware.StopEventType.DWELL_EXIT, "A", 0)));
        recorder.accept(fix("57"), 0, 1, List.of(ev(StopAware.StopEventType.SKIP, "B", 60)));
        recorder.accept(fix("57"), 0, 1, List.of(ev(StopAware.StopEventType.DWELL_ENTER, "C", 150)));

        verify(liveRepository, timeout(2000)).recordTravel("A", "B", 60.0, T0.plusSeconds(60));
        verify(liveRepository, timeout(2000)).recordTravel("B", "C", 90.0, T0.plusSeconds(150));
    }

    @Test
    void directionChangeDropsPendingDeparture() {
        recorder.accept(fix("57"), 0, 1, List.of(ev(StopAware.StopEventType.DWELL_EXIT, "A", 0)));
        recorder.accept(fix("57"), 1, 2, List.of(ev(StopAware.StopEventType.DWELL_ENTER, "B", 90)));

        verify(liveRepository, never()).recordTravel(anyString(), anyString(), anyDouble(), any());
    }

    @Test
    void excludedAxisIsIgnored() {
        recorder.accept(fix("142"), 0, 1, List.of(ev(StopAware.StopEventType.DWELL_EXIT, "A", 0)));
        recorder.accept(fix("142"), 0, 1, List.of(ev(StopAware.StopEventType.DWELL_ENTER, "B", 90)));

        verify(liveRepository, never()).recordTravel(anyString(), anyString(), anyDouble(), any());
    }

    @Test
    void implausibleElapsedIsDropped() {
        recorder.accept(fix("57"), 0, 1, List.of(ev(StopAware.StopEventType.DWELL_EXIT, "A", 0)));
        recorder.accept(fix("57"), 0, 1, List.of(ev(StopAware.StopEventType.DWELL_ENTER, "B", 5)));
        recorder.accept(fix("57"), 0, 1, List.of(ev(StopAware.StopEventType.DWELL_EXIT, "B", 10)));
        recorder.accept(fix("57"), 0, 1,
                List.of(ev(StopAware.StopEventType.DWELL_ENTER, "C", 10 + 2000)));

        verify(liveRepository, never()).recordTravel(anyString(), anyString(), anyDouble(), any());
    }

    @Test
    void repositoryFailureDoesNotPropagate() {
        org.mockito.Mockito.doReturn(Mono.error(new RuntimeException("db down")))
                .when(historyRepository).save(any());
        recorder.accept(fix("57"), 0, 1, List.of(ev(StopAware.StopEventType.DWELL_EXIT, "A", 0)));
        recorder.accept(fix("57"), 0, 1, List.of(ev(StopAware.StopEventType.DWELL_ENTER, "B", 90)));

        verify(historyRepository, timeout(2000)).save(any());
    }

    @Test
    void writeDisabledFlagStopsEverything() {
        properties.setWriteEnabled(false);
        recorder.accept(fix("57"), 0, 1, List.of(ev(StopAware.StopEventType.DWELL_EXIT, "A", 0)));
        recorder.accept(fix("57"), 0, 1, List.of(ev(StopAware.StopEventType.DWELL_ENTER, "B", 90)));

        verify(liveRepository, never()).recordTravel(anyString(), anyString(), anyDouble(), any());
    }
}
