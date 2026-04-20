package biz.ugur.busroutebackend.transport.infrastructure.metrics;

import biz.ugur.busroutebackend.transport.domain.valueobject.OutlierDetectionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.redis.core.ReactiveListOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GpsOutlierMetricsRecorderTest {

    @Mock
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, Object> valueOps;

    @Mock
    private ReactiveListOperations<String, Object> listOps;

    @InjectMocks
    private GpsOutlierMetricsRecorder recorder;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOps);
        lenient().when(valueOps.increment(anyString(), anyLong())).thenReturn(Mono.just(1L));
        lenient().when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        lenient().when(listOps.leftPush(anyString(), any())).thenReturn(Mono.just(1L));
    }

    @Test
    void recordValidEventIncrementsCounters() {
        recorder.record(OutlierDetectionResult.valid("dev-1", 45.0, 33.0, 100, 10));

        GpsOutlierMetricsRecorder.GpsOutlierStats stats = recorder.getStats();
        assertEquals(1, stats.totalDetections());
        assertEquals(1, stats.valid());
        assertEquals(0, stats.outliers());
    }

    @Test
    void recordOutlierTracksMaxSpeed() {
        recorder.record(OutlierDetectionResult.outlier("dev-1", 150.0, 33.0, 1000, 10));

        GpsOutlierMetricsRecorder.GpsOutlierStats stats = recorder.getStats();
        assertEquals(1, stats.outliers());
        assertEquals(150.0, stats.maxImpliedSpeedKmh());
        assertEquals(150.0, stats.avgImpliedSpeedKmh());
    }

    @Test
    void recordBatchAggregatesMultipleResults() {
        recorder.recordBatch(List.of(
                OutlierDetectionResult.valid("d1", 30.0, 33.0, 50, 10),
                OutlierDetectionResult.valid("d2", 25.0, 33.0, 40, 10),
                OutlierDetectionResult.outlier("d3", 200.0, 33.0, 2000, 10),
                OutlierDetectionResult.noHistory("d4", 33.0)
        ));

        GpsOutlierMetricsRecorder.GpsOutlierStats stats = recorder.getStats();
        assertEquals(4, stats.totalDetections());
        assertEquals(2, stats.valid());
        assertEquals(1, stats.outliers());
        assertEquals(1, stats.noHistory());
    }

    @Test
    void outlierRateComputesCorrectly() {
        recorder.record(OutlierDetectionResult.valid("d", 10.0, 33.0, 20, 10));
        recorder.record(OutlierDetectionResult.outlier("d", 200.0, 33.0, 2000, 10));

        GpsOutlierMetricsRecorder.GpsOutlierStats stats = recorder.getStats();
        assertEquals(50.0, stats.outlierRate());
    }

    @Test
    void resetCountersClearsState() {
        recorder.record(OutlierDetectionResult.valid("d", 10.0, 33.0, 20, 10));
        recorder.resetCounters();

        GpsOutlierMetricsRecorder.GpsOutlierStats stats = recorder.getStats();
        assertEquals(0, stats.totalDetections());
        assertEquals(0.0, stats.maxImpliedSpeedKmh());
    }

    @Test
    void totalEvaluatedAndSkippedReported() {
        recorder.record(OutlierDetectionResult.valid("d", 10.0, 33.0, 20, 10));
        recorder.record(OutlierDetectionResult.outlier("d", 200.0, 33.0, 2000, 10));
        recorder.record(OutlierDetectionResult.noHistory("d", 33.0));
        recorder.record(OutlierDetectionResult.disabled("d"));

        GpsOutlierMetricsRecorder.GpsOutlierStats stats = recorder.getStats();
        assertEquals(2, stats.totalEvaluated());
        assertEquals(2, stats.totalSkipped());
    }

    @Test
    void hasOutlierIssuesFlagsHighRate() {
        for (int i = 0; i < 10; i++) {
            recorder.record(OutlierDetectionResult.outlier("d", 200.0, 33.0, 2000, 10));
        }
        recorder.record(OutlierDetectionResult.valid("d", 10.0, 33.0, 20, 10));

        GpsOutlierMetricsRecorder.GpsOutlierStats stats = recorder.getStats();
        assertTrue(stats.hasOutlierIssues());
    }

    @Test
    void emptyStatsReportZeroRates() {
        GpsOutlierMetricsRecorder.GpsOutlierStats stats = recorder.getStats();
        assertEquals(0.0, stats.outlierRate());
        assertEquals(0.0, stats.evaluationRate());
        assertFalse(stats.hasOutlierIssues());
    }
}
