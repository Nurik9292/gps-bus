package biz.ugur.busroutebackend.transport.infrastructure.metrics;

import biz.ugur.busroutebackend.transport.domain.valueobject.GpsValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GpsValidationMetricsRecorderTest {

    @Mock
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, Object> valueOps;

    @InjectMocks
    private GpsValidationMetricsRecorder recorder;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString(), anyLong())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
    }

    @Test
    void recordIncrementsCountersAndTotal() {
        recorder.record(GpsValidationResult.VALID, "dev-1");
        recorder.record(GpsValidationResult.VALID, "dev-1");
        recorder.record(GpsValidationResult.OUT_OF_BOUNDS, "dev-2");

        GpsValidationMetricsRecorder.GpsValidationStats stats = recorder.getStats();
        assertEquals(3, stats.totalValidated());
        assertEquals(2, stats.valid());
        assertEquals(1, stats.outOfBounds());
    }

    @Test
    void recordBatchAggregatesCounts() {
        recorder.recordBatch(Map.of(
                GpsValidationResult.VALID, 10L,
                GpsValidationResult.OUT_OF_BOUNDS, 3L,
                GpsValidationResult.NULL_COORDINATES, 1L
        ));

        GpsValidationMetricsRecorder.GpsValidationStats stats = recorder.getStats();
        assertEquals(14, stats.totalValidated());
        assertEquals(10, stats.valid());
        assertEquals(3, stats.outOfBounds());
        assertEquals(1, stats.nullCoordinates());
    }

    @Test
    void resetCountersReturnsZero() {
        recorder.record(GpsValidationResult.VALID, "dev-1");
        recorder.resetCounters();

        GpsValidationMetricsRecorder.GpsValidationStats stats = recorder.getStats();
        assertEquals(0, stats.totalValidated());
        assertEquals(0, stats.valid());
    }

    @Test
    void statsComputeValidRatePercentage() {
        recorder.record(GpsValidationResult.VALID, "d");
        recorder.record(GpsValidationResult.VALID, "d");
        recorder.record(GpsValidationResult.OUT_OF_BOUNDS, "d");
        recorder.record(GpsValidationResult.OUT_OF_BOUNDS, "d");

        GpsValidationMetricsRecorder.GpsValidationStats stats = recorder.getStats();
        assertEquals(50.0, stats.validRate());
        assertEquals(50.0, stats.outOfBoundsRate());
        assertTrue(stats.hasOutOfBoundsIssues());
    }

    @Test
    void emptyStatsReportFullValidRate() {
        GpsValidationMetricsRecorder.GpsValidationStats stats = recorder.getStats();
        assertEquals(100.0, stats.validRate());
        assertEquals(0.0, stats.outOfBoundsRate());
        assertFalse(stats.hasOutOfBoundsIssues());
    }

    @Test
    void totalInvalidSumsAllInvalidCategories() {
        recorder.recordBatch(Map.of(
                GpsValidationResult.OUT_OF_BOUNDS, 2L,
                GpsValidationResult.NULL_COORDINATES, 3L,
                GpsValidationResult.INVALID_DEVICE_ID, 1L,
                GpsValidationResult.NULL_POSITION, 4L
        ));

        GpsValidationMetricsRecorder.GpsValidationStats stats = recorder.getStats();
        assertEquals(10, stats.totalInvalid());
    }
}
